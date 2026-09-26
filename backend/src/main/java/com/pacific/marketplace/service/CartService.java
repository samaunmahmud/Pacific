package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.CartItem;
import com.pacific.marketplace.domain.DeliveryOption;
import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.PromoCode;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.repo.CartItemRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.CartDtos.CartDto;
import com.pacific.marketplace.web.dto.CartDtos.CartItemDto;
import com.pacific.marketplace.web.dto.CartDtos.DeliveryChoiceDto;
import com.pacific.marketplace.web.dto.CartDtos.ShipmentDto;
import com.pacific.marketplace.web.dto.CartDtos;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartItemRepository cart;
    private final ProductRepository products;
    private final UserRepository users;
    private final ShopPricing pricing;
    private final Delivery delivery;
    private final Promotions promotions;

    public CartService(CartItemRepository cart, ProductRepository products, UserRepository users,
                       ShopPricing pricing, Delivery delivery, Promotions promotions) {
        this.cart = cart;
        this.products = products;
        this.users = users;
        this.pricing = pricing;
        this.delivery = delivery;
        this.promotions = promotions;
    }

    /** The checkout key for a seller's shipment. */
    public static String shipmentKey(SellerProfile seller) {
        return seller == null ? "pacific" : seller.getSlug();
    }

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        return get(userId, null);
    }

    /** The cart with a promo code applied, as a preview (checkout applies it for real). */
    @Transactional(readOnly = true)
    public CartDto get(Long userId, String promoCode) {
        return toDto(cart.findAllForUser(userId), userId, promoCode);
    }

    @Transactional
    public CartDto add(Long userId, Long productId, int quantity) {
        Product product = activeProduct(productId);
        if (product.getSeller() != null && product.getSeller().getUser().getId().equals(userId)) {
            throw ApiException.conflict("You can't buy your own product.");
        }
        CartItem item = cart.findByUserIdAndProductId(userId, productId).orElse(null);
        int wanted = (item == null ? 0 : item.getQuantity()) + quantity;
        checkAvailable(product, wanted);
        if (item == null) {
            cart.save(new CartItem(users.getReferenceById(userId), product, wanted));
        } else {
            item.setQuantity(wanted);
            item.setSavedForLater(false); // adding it again means they want it now
        }
        cart.flush();
        return get(userId);
    }

    /** Sets the quantity; 0 removes the line. */
    @Transactional
    public CartDto setQuantity(Long userId, Long productId, int quantity) {
        CartItem item = cart.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> ApiException.notFound("That item isn't in your cart."));
        if (quantity == 0) {
            cart.delete(item);
        } else {
            checkAvailable(activeProduct(productId), quantity);
            item.setQuantity(quantity);
        }
        cart.flush();
        return get(userId);
    }

    /** "Save for later" (or, with false, "Move to cart"). */
    @Transactional
    public CartDto saveForLater(Long userId, Long productId, boolean saved) {
        CartItem item = cart.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> ApiException.notFound("That item isn't in your cart."));
        if (!saved) checkAvailable(activeProduct(productId), item.getQuantity());
        item.setSavedForLater(saved);
        cart.flush();
        return get(userId);
    }

    @Transactional
    public CartDto remove(Long userId, Long productId) {
        cart.findByUserIdAndProductId(userId, productId).ifPresent(cart::delete);
        cart.flush();
        return get(userId);
    }

    /**
     * Puts the items of a released, unpaid card checkout back in the customer's cart so they can try again.
     * Best effort: a product that is no longer for sale is skipped and the quantity is capped at what is in stock and
     * at the per-item limit. It only tops a line up to the released quantity and never adds on top of one the
     * customer has since added themselves, so running it twice changes nothing.
     *
     * @param released product id to the quantity that was reserved for the checkout
     */
    @Transactional
    public void restore(Long userId, Map<Long, Integer> released) {
        int max = pricing.maxQuantityPerItem();
        released.forEach((productId, quantity) -> {
            int available = products.findAvailableStock(productId).orElse(0);
            CartItem existing = cart.findByUserIdAndProductId(userId, productId).orElse(null);
            int have = existing == null ? 0 : existing.getQuantity();
            int target = Math.min(Math.max(have, quantity), Math.min(max, available));
            if (existing != null) existing.setSavedForLater(false); // back in the cart they were paying for
            if (target <= have) return;
            if (existing == null) {
                cart.save(new CartItem(users.getReferenceById(userId), products.getReferenceById(productId), target));
            } else {
                existing.setQuantity(target);
            }
        });
        cart.flush();
    }

    private Product activeProduct(Long productId) {
        return products.findById(productId).filter(Product::isVisibleInStore)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    private void checkAvailable(Product product, int wanted) {
        int max = pricing.maxQuantityPerItem();
        if (wanted > max) {
            throw ApiException.conflict("You can buy at most " + max + " of each item per order.");
        }
        if (product.getStock() < wanted) {
            throw ApiException.conflict(product.getStock() == 0
                    ? product.getName() + " is out of stock."
                    : "Only " + product.getStock() + " of " + product.getName() + " left in stock.");
        }
    }

    private static CartItemDto line(CartItem i, OrderItem.Pricing price) {
        Product p = i.getProduct();
        var seller = p.getSeller();
        boolean discounted = price.unitPrice().compareTo(p.getPrice()) != 0;
        return new CartItemDto(p.getId(), p.getName(), p.getImageUrl(),
                p.getCategory() == null ? null : p.getCategory().getName(), price.unitPrice(), i.getQuantity(),
                price.unitPrice().multiply(BigDecimal.valueOf(i.getQuantity())), p.getStock(),
                seller == null ? "Pacific" : seller.getStoreName(), seller == null ? null : seller.getSlug(),
                discounted ? p.getPrice() : null, price.label());
    }

    /**
     * Prices the lines being bought: each gets its Lightning Deal or clipped coupon, then a promo code takes its
     * percentage off its store's lines. Checkout prices the same way (see OrderService). {@code promoError} explains a
     * code that can't be used.
     */
    public record Priced(Map<CartItem, OrderItem.Pricing> prices, PromoCode code, BigDecimal promoDiscount, String promoError) {
    }

    public Priced price(List<CartItem> items, Long userId, String promoCode) {
        Promotions.Live live = promotions.live(items.stream().map(i -> i.getProduct().getId()).toList(), userId);
        Map<CartItem, OrderItem.Pricing> prices = new LinkedHashMap<>();
        Map<String, BigDecimal> storeSubtotals = new LinkedHashMap<>();
        for (CartItem i : items) {
            OrderItem.Pricing p = Promotions.price(i.getProduct(), i.getQuantity(), live);
            prices.put(i, p);
            storeSubtotals.merge(shipmentKey(i.getProduct().getSeller()), p.unitPrice().multiply(BigDecimal.valueOf(i.getQuantity())), BigDecimal::add);
        }
        String raw = Text.clean(promoCode);
        if (raw == null) return new Priced(prices, null, BigDecimal.ZERO, null);
        PromoCode code;
        try {
            code = promotions.checkCode(raw, userId, storeSubtotals);
        } catch (ApiException e) {
            return new Priced(prices, null, BigDecimal.ZERO, e.getMessage());
        }
        String store = shipmentKey(code.getSeller());
        BigDecimal saved = BigDecimal.ZERO;
        for (Map.Entry<CartItem, OrderItem.Pricing> e : prices.entrySet()) {
            if (!shipmentKey(e.getKey().getProduct().getSeller()).equals(store)) continue;
            OrderItem.Pricing withCode = Promotions.withCode(e.getValue(), code);
            saved = saved.add(e.getValue().unitPrice().subtract(withCode.unitPrice()).multiply(BigDecimal.valueOf(e.getKey().getQuantity())));
            e.setValue(withCode);
        }
        return new Priced(prices, code, saved.setScale(2), null);
    }

    private CartDto toDto(List<CartItem> all, Long userId, String promoCode) {
        List<CartItem> items = all.stream().filter(i -> !i.isSavedForLater()).toList();
        Priced priced = price(items, userId, promoCode);
        List<CartItemDto> lines = items.stream().map(i -> line(i, priced.prices().get(i))).toList();
        Promotions.Live savedLive = promotions.live(all.stream().filter(CartItem::isSavedForLater).map(i -> i.getProduct().getId()).toList(), userId);
        List<CartItemDto> saved = all.stream().filter(CartItem::isSavedForLater)
                .map(i -> line(i, Promotions.price(i.getProduct(), i.getQuantity(), savedLive))).toList();

        // Each seller ships separately, so delivery (and its free threshold) applies per seller.
        Map<String, BigDecimal> subtotalByKey = new LinkedHashMap<>();
        Map<String, SellerProfile> sellerByKey = new LinkedHashMap<>();
        for (CartItem i : items) {
            SellerProfile seller = i.getProduct().getSeller();
            String key = shipmentKey(seller);
            subtotalByKey.merge(key, priced.prices().get(i).unitPrice().multiply(BigDecimal.valueOf(i.getQuantity())), BigDecimal::add);
            sellerByKey.putIfAbsent(key, seller);
        }
        List<ShipmentDto> shipments = subtotalByKey.entrySet().stream().map(e -> {
            BigDecimal sub = e.getValue().setScale(2);
            SellerProfile seller = sellerByKey.get(e.getKey());
            BigDecimal threshold = delivery.freeThreshold(seller);
            List<DeliveryChoiceDto> choices = java.util.Arrays.stream(DeliveryOption.values()).map(o -> {
                Delivery.Window w = delivery.window(o, seller);
                return new DeliveryChoiceDto(o, o.label(), delivery.fee(o, sub, seller), w.from(), w.to());
            }).toList();
            return new ShipmentDto(e.getKey(), seller == null ? "Pacific" : seller.getStoreName(),
                    seller == null ? null : seller.getSlug(), sub, choices.get(0).fee(), threshold,
                    threshold.subtract(sub).max(BigDecimal.ZERO).setScale(2), choices);
        }).toList();

        BigDecimal subtotal = shipments.stream().map(ShipmentDto::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        BigDecimal shipping = shipments.stream().map(ShipmentDto::shipping).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        PromoCode code = priced.code();
        CartDtos.PromoDto promo = code == null ? null : new CartDtos.PromoDto(code.getCode(), code.getPercentOff(),
                code.getSeller() == null ? "Pacific" : code.getSeller().getStoreName(), priced.promoDiscount());
        return new CartDto(lines, shipments, lines.stream().mapToInt(CartItemDto::quantity).sum(), subtotal,
                shipping, subtotal.add(shipping), pricing.freeShippingThreshold(), saved, delivery.orderWithin(),
                promo, priced.promoError());
    }
}
