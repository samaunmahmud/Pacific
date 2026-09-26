package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Coupon;
import com.pacific.marketplace.domain.CouponClip;
import com.pacific.marketplace.domain.LightningDeal;
import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.PromoCode;
import com.pacific.marketplace.domain.PromoRedemption;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.repo.CouponClipRepository;
import com.pacific.marketplace.repo.CouponRepository;
import com.pacific.marketplace.repo.LightningDealRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.PromoCodeRepository;
import com.pacific.marketplace.repo.PromoRedemptionRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lightning Deals, coupons and promo codes. Each is run, and paid for, by one store (a seller's, or Pacific's own for
 * house products), and each lowers the price paid per unit, so orders, refunds and seller earnings need nothing
 * special. A line gets the better of its deal and a clipped coupon; a promo code then takes a percentage off that
 * store's lines. Checkout claims deal units, coupon uses and code uses with atomic updates, and a cancelled order
 * gives them back.
 */
@Service
public class Promotions {

    public static final Duration MAX_DEAL = Duration.ofHours(12);
    public static final Duration MIN_DEAL = Duration.ofHours(1);

    private final LightningDealRepository deals;
    private final CouponRepository coupons;
    private final CouponClipRepository clips;
    private final PromoCodeRepository codes;
    private final PromoRedemptionRepository redemptions;
    private final ProductRepository products;
    private final UserRepository users;
    private final BuyBox buyBox;

    public Promotions(LightningDealRepository deals, CouponRepository coupons, CouponClipRepository clips,
                      PromoCodeRepository codes, PromoRedemptionRepository redemptions, ProductRepository products,
                      UserRepository users, BuyBox buyBox) {
        this.deals = deals;
        this.coupons = coupons;
        this.clips = clips;
        this.codes = codes;
        this.redemptions = redemptions;
        this.products = products;
        this.users = users;
        this.buyBox = buyBox;
    }

    // ---------- what's on now ----------

    /** Promotions running on some listings, and which coupons a customer has clipped (none when signed out). */
    public record Live(Map<Long, LightningDeal> deals, Map<Long, Coupon> coupons, Set<Long> clipped) {
        public static final Live NONE = new Live(Map.of(), Map.of(), Set.of());
    }

    @Transactional(readOnly = true)
    public Live live(Collection<Long> productIds, Long userId) {
        if (productIds.isEmpty()) return Live.NONE;
        Instant now = Instant.now();
        Map<Long, LightningDeal> byProduct = new HashMap<>();
        deals.findLive(productIds, now).forEach(d -> byProduct.putIfAbsent(d.getProduct().getId(), d));
        Map<Long, Coupon> couponByProduct = new HashMap<>();
        coupons.findLive(productIds, now).forEach(c -> couponByProduct.putIfAbsent(c.getProduct().getId(), c));
        Set<Long> clipped = userId == null || couponByProduct.isEmpty() ? Set.of()
                : new HashSet<>(clips.findUnusedClips(userId, couponByProduct.values().stream().map(Coupon::getId).toList()));
        return new Live(byProduct, couponByProduct, clipped);
    }

    public static com.pacific.marketplace.web.dto.ProductDtos.DealDto dealDto(Live live, Long productId) {
        LightningDeal d = live.deals().get(productId);
        return d == null ? null : com.pacific.marketplace.web.dto.ProductDtos.DealDto.from(d);
    }

    public static com.pacific.marketplace.web.dto.ProductDtos.CouponDto couponDto(Live live, Long productId) {
        Coupon c = live.coupons().get(productId);
        return c == null ? null : new com.pacific.marketplace.web.dto.ProductDtos.CouponDto(c.getId(), productId,
                c.getPercentOff(), live.clipped().contains(c.getId()));
    }

    @Transactional(readOnly = true)
    public boolean anyLiveDeal(Collection<Long> productIds) {
        return !productIds.isEmpty() && !deals.findLive(productIds, Instant.now()).isEmpty();
    }

    /** The better of the listing's deal (if enough units are left) and its coupon (if clipped). */
    public static OrderItem.Pricing price(Product p, int quantity, Live live) {
        BigDecimal unit = p.getPrice();
        OrderItem.Pricing best = OrderItem.Pricing.regular(p);
        LightningDeal deal = live.deals().get(p.getId());
        if (deal != null && deal.getQuantity() - deal.getClaimed() >= quantity && deal.getDealPrice().compareTo(unit) < 0) {
            best = new OrderItem.Pricing(deal.getDealPrice(), deal.getId(), null, null, "Lightning Deal");
        }
        Coupon coupon = live.coupons().get(p.getId());
        if (coupon != null && live.clipped().contains(coupon.getId())) {
            BigDecimal withCoupon = percentOff(unit, coupon.getPercentOff());
            if (withCoupon.compareTo(best.unitPrice()) < 0) {
                best = new OrderItem.Pricing(withCoupon, null, coupon.getId(), null, "Coupon " + coupon.getPercentOff() + "%");
            }
        }
        return best;
    }

    /** A promo code's percentage off one line (on top of its deal or coupon). */
    public static OrderItem.Pricing withCode(OrderItem.Pricing line, PromoCode code) {
        String label = (line.label() == null ? "" : line.label() + " · ") + "Code " + code.getCode();
        return new OrderItem.Pricing(percentOff(line.unitPrice(), code.getPercentOff()), line.dealId(), line.couponId(),
                code.getId(), label);
    }

    static BigDecimal percentOff(BigDecimal price, int percent) {
        return price.multiply(BigDecimal.valueOf(100 - percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Checks a promo code for this cart. {@code storeSubtotals} is each store's subtotal after deals and coupons, by
     * shipment key. Returns the code, or throws with a message the shopper can act on.
     */
    // Deliberately not @Transactional: the cart catches the error to show it, which a transaction here would turn into
    // a rollback of the whole request. It runs inside its caller's transaction.
    public PromoCode checkCode(String raw, Long userId, Map<String, BigDecimal> storeSubtotals) {
        PromoCode code = codes.findByCodeIgnoreCase(raw.strip())
                .orElseThrow(() -> ApiException.badRequest("\"" + raw.strip() + "\" isn't a valid promo code."));
        Instant now = Instant.now();
        if (!code.isActive() || !now.isBefore(code.getEndsAt())) throw ApiException.badRequest("That promo code has expired.");
        if (!code.isLive(now)) throw ApiException.badRequest("That promo code has been used up.");
        if (userId != null && redemptions.existsByPromoIdAndUserId(code.getId(), userId)) {
            throw ApiException.badRequest("You've already used that promo code.");
        }
        String store = code.getSeller() == null ? "Pacific" : code.getSeller().getStoreName();
        BigDecimal subtotal = storeSubtotals.get(CartService.shipmentKey(code.getSeller()));
        if (subtotal == null) throw ApiException.badRequest("That code is for items sold by " + store + ", and there are none in your cart.");
        if (subtotal.compareTo(code.getMinSpend()) < 0) {
            throw ApiException.badRequest("Spend " + com.pacific.marketplace.domain.Money.format(code.getMinSpend(), "GBP")
                    + " on " + store + " items to use this code.");
        }
        return code;
    }

    // ---------- checkout ----------

    /**
     * Takes what an order's lines used: deal units, a coupon use per clipped coupon, and one use of the promo code.
     * Throws (rolling the checkout back) if any has run out since the cart was priced.
     */
    @Transactional
    public void claim(Order order, Long userId) {
        Instant now = Instant.now();
        Set<Long> codesUsed = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getDealId() != null && deals.claim(item.getDealId(), item.getQuantity(), now) == 0) {
                throw ApiException.conflict("The Lightning Deal on " + item.getProductName()
                        + " has just ended or sold out. Please check your cart.");
            }
            if (item.getCouponId() != null && (clips.redeem(item.getCouponId(), userId, order.getId()) == 0
                    || coupons.use(item.getCouponId(), now) == 0)) {
                throw ApiException.conflict("The coupon on " + item.getProductName() + " is no longer available. Please check your cart.");
            }
            if (item.getPromoId() != null && codesUsed.add(item.getPromoId())) {
                if (codes.use(item.getPromoId(), now) == 0) throw ApiException.conflict("That promo code has just been used up.");
                try {
                    redemptions.saveAndFlush(new PromoRedemption(codes.getReferenceById(item.getPromoId()),
                            users.getReferenceById(userId), order.getId()));
                } catch (DataIntegrityViolationException e) {
                    throw ApiException.conflict("You've already used that promo code.");
                }
            }
        }
        if (order.getItems().stream().anyMatch(i -> i.getDealId() != null)) {
            buyBox.refreshFor(order.getItems().stream().map(i -> i.getProduct().getId()).toList()); // a deal may have sold out
        }
    }

    /** A cancelled order gives back its deal units, coupon uses and promo code use. */
    @Transactional
    public void release(Order order) {
        Set<Long> codesReleased = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getDealId() != null) deals.release(item.getDealId(), item.getQuantity());
            if (item.getCouponId() != null && clips.release(item.getCouponId(), order.getId()) > 0) coupons.release(item.getCouponId());
            if (item.getPromoId() != null && codesReleased.add(item.getPromoId())
                    && redemptions.deleteForOrder(item.getPromoId(), order.getId()) > 0) {
                codes.release(item.getPromoId());
            }
        }
    }

    // ---------- shoppers ----------

    /** "Apply coupon": clips it for the customer (nothing changes if they already have). */
    @Transactional
    public void clip(Long userId, Long couponId) {
        Coupon coupon = coupons.findById(couponId).filter(c -> c.isLive(Instant.now()))
                .orElseThrow(() -> ApiException.notFound("That coupon has ended."));
        if (clips.findByCouponIdAndUserId(coupon.getId(), userId).isPresent()) return;
        try {
            clips.saveAndFlush(new CouponClip(coupon, users.getReferenceById(userId)));
        } catch (DataIntegrityViolationException e) {
            // clipped twice at once: one is enough
        }
    }

    // ---------- stores ----------

    /** A listing the store (seller id, or null for Pacific's own) can run promotions on. */
    private Product ownListing(Long sellerId, Long productId) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found."));
        boolean mine = sellerId == null ? p.getSeller() == null : p.getSeller() != null && p.getSeller().getId().equals(sellerId);
        if (!mine) throw ApiException.notFound("Product not found.");
        if (!p.isActive()) throw ApiException.badRequest("That listing is hidden. Make it live first.");
        return p;
    }

    @Transactional
    public LightningDeal createDeal(Long sellerId, Long productId, BigDecimal price, int quantity, Instant startsAt, int hours) {
        Product p = ownListing(sellerId, productId);
        Instant now = Instant.now();
        Instant start = startsAt == null || startsAt.isBefore(now) ? now : startsAt;
        if (start.isAfter(now.plus(Duration.ofDays(14)))) throw ApiException.badRequest("A deal can start at most 14 days from now.");
        Duration length = Duration.ofHours(hours);
        if (length.compareTo(MIN_DEAL) < 0 || length.compareTo(MAX_DEAL) > 0) {
            throw ApiException.badRequest("A Lightning Deal runs for 1 to 12 hours.");
        }
        if (price.compareTo(p.getPrice()) >= 0) throw ApiException.badRequest("The deal price must be below the listing's price.");
        if (percentOffRegular(price, p.getPrice()) < 5) throw ApiException.badRequest("A Lightning Deal needs at least 5% off.");
        if (quantity > p.getStock()) throw ApiException.badRequest("You only have " + p.getStock() + " in stock.");
        if (deals.overlaps(p.getId(), start, start.plus(length))) {
            throw ApiException.conflict("That listing already has a deal at that time.");
        }
        LightningDeal deal = deals.saveAndFlush(new LightningDeal(p, price, quantity, start, start.plus(length)));
        if (!start.isAfter(now)) buyBox.refresh(p);
        return deal;
    }

    @Transactional
    public LightningDeal endDeal(Long sellerId, Long dealId) {
        LightningDeal deal = deals.findById(dealId).orElseThrow(() -> ApiException.notFound("Deal not found."));
        ownListing(sellerId, deal.getProduct().getId());
        deal.endNow(Instant.now());
        deals.flush();
        buyBox.refresh(deal.getProduct());
        return deal;
    }

    @Transactional
    public Coupon createCoupon(Long sellerId, Long productId, int percentOff, int budget, int days) {
        Product p = ownListing(sellerId, productId);
        Instant now = Instant.now();
        if (coupons.existsRunning(p.getId(), now)) throw ApiException.conflict("That listing already has a coupon running.");
        return coupons.saveAndFlush(new Coupon(p, percentOff, budget, now.plus(Duration.ofDays(days))));
    }

    @Transactional
    public Coupon stopCoupon(Long sellerId, Long couponId) {
        Coupon c = coupons.findById(couponId).orElseThrow(() -> ApiException.notFound("Coupon not found."));
        ownListing(sellerId, c.getProduct().getId());
        c.deactivate();
        return c;
    }

    @Transactional
    public PromoCode createCode(SellerProfile store, String rawCode, int percentOff, BigDecimal minSpend, Integer maxUses, int days) {
        String code = rawCode.strip().toUpperCase(Locale.ROOT);
        if (codes.existsByCodeIgnoreCase(code)) throw ApiException.conflict("That code is taken. Try another.");
        try {
            return codes.saveAndFlush(new PromoCode(code, store, percentOff, minSpend, maxUses, Instant.now().plus(Duration.ofDays(days))));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("That code is taken. Try another.");
        }
    }

    @Transactional
    public PromoCode stopCode(Long sellerId, Long codeId) {
        PromoCode c = codes.findById(codeId).orElseThrow(() -> ApiException.notFound("Code not found."));
        boolean mine = sellerId == null ? c.getSeller() == null : c.getSeller() != null && c.getSeller().getId().equals(sellerId);
        if (!mine) throw ApiException.notFound("Code not found.");
        c.deactivate();
        return c;
    }

    /** A store's promotions that are running, scheduled, or ended in the last 30 days. */
    @Transactional(readOnly = true)
    public List<LightningDeal> storeDeals(Long sellerId) {
        return deals.findForStore(sellerId, Instant.now().minus(Duration.ofDays(30)));
    }

    @Transactional(readOnly = true)
    public List<Coupon> storeCoupons(Long sellerId) {
        return coupons.findForStore(sellerId, Instant.now().minus(Duration.ofDays(30)));
    }

    @Transactional(readOnly = true)
    public List<PromoCode> storeCodes(Long sellerId) {
        return codes.findForStore(sellerId, Instant.now().minus(Duration.ofDays(30)));
    }

    public static int percentOffRegular(BigDecimal price, BigDecimal regular) {
        return regular.subtract(price).multiply(BigDecimal.valueOf(100)).divide(regular, 0, RoundingMode.DOWN).intValue();
    }
}
