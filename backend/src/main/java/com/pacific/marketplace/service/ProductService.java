package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ProductDtos.CategoryDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BuyBox buyBox;
    private final Promotions promotions;
    private final VariationService variations;

    public ProductService(ProductRepository products, CategoryRepository categories, BuyBox buyBox, Promotions promotions,
                          VariationService variations) {
        this.products = products;
        this.categories = categories;
        this.buyBox = buyBox;
        this.promotions = promotions;
        this.variations = variations;
    }

    /**
     * Adds the Lightning Deal and coupon running on the listing each card would buy, and how many variations a card
     * stands for.
     */
    public List<ProductDto> decorate(List<ProductDto> cards) {
        if (cards.isEmpty()) return cards;
        java.util.function.Function<ProductDto, Long> buys = d -> d.catalogId().equals(d.id()) ? d.boxProductId() : d.id();
        Promotions.Live live = promotions.live(cards.stream().map(buys).toList(), null);
        Map<Long, Integer> counts = variations.countsOnSale(cards.stream()
                .filter(d -> d.variation() != null && d.catalogId().equals(d.id())).map(ProductDto::id).toList());
        return cards.stream().map(d -> d.withPromotions(Promotions.dealDto(live, buys.apply(d)),
                Promotions.couponDto(live, buys.apply(d))).withVariationCount(counts.getOrDefault(d.id(), 0))).toList();
    }

    /**
     * Today's Deals: a "was" price, or a Lightning Deal or coupon running on any of the product's listings (for a
     * store's page, on that listing).
     */
    private static Specification<Product> onDeal(boolean catalog) {
        return (root, cq, cb) -> {
            Instant now = Instant.now();
            var deal = cq.subquery(Long.class);
            var d = deal.from(com.pacific.marketplace.domain.LightningDeal.class);
            var dp = d.join("product");
            deal.select(d.get("id")).where(inGroup(cb, dp, root, catalog), cb.lessThanOrEqualTo(d.get("startsAt"), now),
                    cb.greaterThan(d.get("endsAt"), now), cb.lessThan(d.get("claimed"), d.<Integer>get("quantity")));
            var coupon = cq.subquery(Long.class);
            var c = coupon.from(com.pacific.marketplace.domain.Coupon.class);
            var cp = c.join("product");
            coupon.select(c.get("id")).where(inGroup(cb, cp, root, catalog), cb.isTrue(c.get("active")),
                    cb.greaterThan(c.get("endsAt"), now), cb.lessThan(c.get("used"), c.<Integer>get("budget")));
            return cb.or(cb.greaterThan(root.<Integer>get("discountPercent"), 0), cb.exists(deal), cb.exists(coupon));
        };
    }

    private static jakarta.persistence.criteria.Predicate inGroup(jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Path<?> listing, jakarta.persistence.criteria.Root<Product> page, boolean catalog) {
        return catalog ? cb.or(cb.equal(listing.get("id"), page.get("id")), cb.equal(listing.get("groupId"), page.get("id")))
                : cb.equal(listing.get("id"), page.get("id"));
    }

    // ---------- storefront ----------

    /** Only products shoppers can buy: active, and either Pacific's own or from an approved seller. */
    private static Specification<Product> storefront() {
        return (root, cq, cb) -> {
            Join<Object, Object> seller = root.join("seller", JoinType.LEFT);
            return cb.and(cb.isTrue(root.get("active")),
                    cb.or(cb.isNull(seller.get("id")), cb.equal(seller.get("status"), SellerStatus.APPROVED)));
        };
    }

    /** Can this listing be bought? The same rule as {@link #storefront()}, for a listing joined in a subquery. */
    private static jakarta.persistence.criteria.Predicate onSale(jakarta.persistence.criteria.From<?, Product> p,
                                                                 jakarta.persistence.criteria.CriteriaBuilder cb) {
        Join<Object, Object> seller = p.join("seller", JoinType.LEFT);
        return cb.and(cb.isTrue(p.get("active")),
                cb.or(cb.isNull(seller.get("id")), cb.equal(seller.get("status"), SellerStatus.APPROVED)));
    }

    /**
     * Catalog pages shoppers can see: one per product however many sellers offer it, shown while any of its listings
     * is on sale (so a product stays in search when its first seller runs out but another still has it). A product
     * with variations shows once too, as its first variation shoppers can see.
     */
    private static Specification<Product> catalog() {
        return (root, cq, cb) -> {
            var others = cq.subquery(Long.class);
            var o = others.from(Product.class);
            others.select(o.get("id")).where(cb.equal(o.get("groupId"), root.get("id")), onSale(o, cb));
            var earlier = cq.subquery(Long.class);
            var e = earlier.from(Product.class);
            var earlierOffers = earlier.subquery(Long.class);
            var eo = earlierOffers.from(Product.class);
            earlierOffers.select(eo.get("id")).where(cb.equal(eo.get("groupId"), e.get("id")), onSale(eo, cb));
            earlier.select(e.get("id")).where(cb.equal(e.get("familyId"), root.get("familyId")),
                    cb.lessThan(e.get("id"), root.get("id")), cb.or(onSale(e, cb), cb.exists(earlierOffers)));
            return cb.and(cb.isNull(root.get("groupId")), cb.or(onSale(root, cb), cb.exists(others)),
                    cb.or(cb.isNull(root.get("familyId")), cb.not(cb.exists(earlier))));
        };
    }

    /**
     * A product as shoppers see it on a card or page: for a catalog page, "Add to cart" buys the buy-box listing, which
     * may be another seller's (only looked up when it could differ from the page's own listing).
     */
    public ProductDto card(Product p) {
        ProductDto dto = ProductDto.from(p);
        if (p.isOffer() || (p.getOfferCount() == 1 && p.isVisibleInStore() && p.getStock() > 0)) return dto;
        List<BuyBox.Listing> ranked = buyBox.ranked(p.getId());
        if (ranked.isEmpty()) return dto;
        BuyBox.Listing box = ranked.get(0);
        String seller = box.id().equals(p.getId()) ? dto.sellerName()
                : products.findWithCategoryById(box.id()).map(o -> ProductDto.from(o).sellerName()).orElse(dto.sellerName());
        return dto.withBuyBox(box.id(), box.price(), box.stock(), seller, ranked.size());
    }

    /** Optional narrowing for storefront searches: a price range and a minimum average rating. */
    public record Filters(BigDecimal minPrice, BigDecimal maxPrice, Double minRating) {
        public static final Filters NONE = new Filters(null, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, String categorySlug, String sellerSlug, boolean dealsOnly,
                                           String sort, int page, int size) {
        return search(q, categorySlug, sellerSlug, dealsOnly, Filters.NONE, sort, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, String categorySlug, String sellerSlug, boolean dealsOnly,
                                           Filters filters, String sort, int page, int size) {
        // A store's page lists that store's own listings; everywhere else shows one card per product, priced at its
        // buy box.
        String seller = Text.clean(sellerSlug);
        boolean catalog = seller == null;
        String priceField = catalog ? "boxPrice" : "price";
        Specification<Product> spec = catalog ? catalog() : storefront();
        if (filters.minPrice() != null) {
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get(priceField), filters.minPrice()));
        }
        if (filters.maxPrice() != null) {
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.<BigDecimal>get(priceField), filters.maxPrice()));
        }
        if (filters.minRating() != null) {
            BigDecimal min = BigDecimal.valueOf(filters.minRating());
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("ratingAvg"), min));
        }
        if (seller != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("seller").get("slug"), seller));
        if (dealsOnly) spec = spec.and(onDeal(catalog));
        PageResponse<ProductDto> found = page(spec, q, categorySlug, sort, priceField, page, size, this::card, catalog);
        return new PageResponse<>(decorate(found.items()), found.page(), found.size(), found.totalItems(), found.totalPages());
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        // An offer's id opens the product's catalog page, which is what shoppers see.
        Long catalogId = products.findById(id).map(Product::catalogId).orElse(id);
        if (!buyBox.catalogVisible(catalogId)) throw ApiException.notFound("Product not found.");
        return products.findWithCategoryById(catalogId)
                .map(p -> decorate(List.of(card(p))).get(0).withVariations(variations.forShoppers(p)))
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    /** For the "recently viewed" strip: the visible products among {@code ids}, in the order requested. */
    @Transactional(readOnly = true)
    public List<ProductDto> batch(List<Long> ids) {
        List<Long> wanted = ids.stream().distinct().limit(20).toList();
        Map<Long, Product> byId = new HashMap<>();
        products.findWithCategoryByIdIn(wanted).stream().filter(p -> !p.isOffer() && buyBox.catalogVisible(p.getId()))
                .forEach(p -> byId.put(p.getId(), p));
        return decorate(wanted.stream().map(byId::get).filter(p -> p != null).map(this::card).toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryDto::from).toList();
    }

    // ---------- admin: products (Pacific's own catalogue, but admins can manage any listing) ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> adminSearch(String q, String categorySlug, String sort, int page, int size) {
        return page((root, cq, cb) -> cb.conjunction(), q, categorySlug, sort, "price", page, size, ProductDto::from, false);
    }

    @Transactional(readOnly = true)
    public ProductDto adminGet(Long id) {
        Product p = find(id, null);
        return ProductDto.from(p).withVariations(variations.forOwner(p));
    }

    @Transactional
    public ProductDto create(ProductRequest req) {
        return createFor(req, null);
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest req) {
        return updateFor(id, req, null);
    }

    @Transactional
    public ProductDto setStock(Long id, int stock) {
        return stockFor(id, stock, null);
    }

    /** Products are never hard-deleted (orders, reviews and carts refer to them); "delete" hides them. */
    @Transactional
    public ProductDto deactivate(Long id) {
        return deactivateFor(id, null);
    }

    // ---------- seller: their own listings ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> sellerSearch(Long sellerId, String q, int page, int size) {
        return page((root, cq, cb) -> cb.equal(root.get("seller").get("id"), sellerId), q, null, "newest", "price", page, size,
                ProductDto::from, false);
    }

    @Transactional(readOnly = true)
    public ProductDto sellerGet(Long sellerId, Long id) {
        Product p = find(id, sellerId);
        return ProductDto.from(p).withVariations(variations.forOwner(p));
    }

    @Transactional
    public ProductDto sellerCreate(SellerProfile seller, ProductRequest req) {
        return createFor(req, seller);
    }

    @Transactional
    public ProductDto sellerUpdate(Long sellerId, Long id, ProductRequest req) {
        return updateFor(id, req, sellerId);
    }

    @Transactional
    public ProductDto sellerSetStock(Long sellerId, Long id, int stock) {
        return stockFor(id, stock, sellerId);
    }

    @Transactional
    public ProductDto sellerDeactivate(Long sellerId, Long id) {
        return deactivateFor(id, sellerId);
    }

    // ---------- admin: categories ----------

    @Transactional
    public CategoryDto createCategory(String name) {
        String clean = name.strip();
        checkNameFree(clean, null);
        Category c = new Category(clean, uniqueSlug(clean, null));
        try {
            return CategoryDto.from(categories.saveAndFlush(c));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("A category with that name already exists.");
        }
    }

    @Transactional
    public CategoryDto renameCategory(Long id, String name) {
        Category c = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category not found."));
        String clean = name.strip();
        checkNameFree(clean, c);
        c.rename(clean, uniqueSlug(clean, c));
        return CategoryDto.from(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categories.existsById(id)) throw ApiException.notFound("Category not found.");
        categories.deleteById(id); // products keep existing; the FK sets their category to null
    }

    // ---------- helpers ----------

    private ProductDto createFor(ProductRequest req, SellerProfile seller) {
        checkPrices(req);
        List<String> photos = photos(req.imageUrl(), req.moreImages() == null ? List.of() : req.moreImages());
        Product p = new Product(req.name().strip(), Text.clean(req.description()), req.price(), req.stock(),
                cover(photos), category(req.categoryId()));
        p.setSeller(seller);
        p.update(p.getName(), p.getDescription(), req.price(), req.listPrice(), req.stock(), p.getImageUrl(),
                p.getCategory(), !Boolean.FALSE.equals(req.active()));
        p.setMoreImages(rest(photos));
        return ProductDto.from(products.save(p));
    }

    private ProductDto updateFor(Long id, ProductRequest req, Long sellerId) {
        checkPrices(req);
        Product p = find(id, sellerId);
        boolean active = req.active() == null ? p.isActive() : req.active();
        if (p.isOffer()) {
            // An offer's name, photos and description come from the catalog page; only its own terms change here.
            p.update(p.getName(), p.getDescription(), req.price(), req.listPrice(), req.stock(), p.getImageUrl(),
                    p.getCategory(), active);
            if (req.condition() != null) p.setCondition(req.condition());
        } else {
            List<String> photos = photos(req.imageUrl(), req.moreImages() == null ? p.getMoreImages() : req.moreImages());
            p.update(req.name().strip(), Text.clean(req.description()), req.price(), req.listPrice(), req.stock(),
                    cover(photos), category(req.categoryId()), active);
            p.setMoreImages(rest(photos));
            // Other sellers' offers show the catalog page's details (in carts and orders too), so keep them in step.
            products.findByGroupId(p.getId()).forEach(offer -> offer.copyCatalogDetails(p));
        }
        buyBox.refresh(p);
        return ProductDto.from(p);
    }

    private ProductDto stockFor(Long id, int stock, Long sellerId) {
        Product p = find(id, sellerId);
        p.setStock(stock);
        buyBox.refresh(p);
        return ProductDto.from(p);
    }

    private ProductDto deactivateFor(Long id, Long sellerId) {
        Product p = find(id, sellerId);
        p.setActive(false);
        buyBox.refresh(p);
        return ProductDto.from(p);
    }

    /**
     * All of a product's photos in order, main one first, without blanks or repeats. With no main photo the first
     * extra one takes its place, so a product never has a gallery but no picture on its card.
     */
    private static List<String> photos(String imageUrl, List<String> more) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        String main = Text.clean(imageUrl);
        if (main != null) all.add(main);
        more.stream().map(Text::clean).filter(u -> u != null).forEach(all::add);
        return List.copyOf(all);
    }

    private static String cover(List<String> photos) {
        return photos.isEmpty() ? null : photos.get(0);
    }

    private static List<String> rest(List<String> photos) {
        return photos.isEmpty() ? List.of() : photos.subList(1, photos.size());
    }

    private static void checkPrices(ProductRequest req) {
        BigDecimal list = req.listPrice();
        if (list != null && list.compareTo(req.price()) <= 0) {
            throw ApiException.badRequest("The list price (the \"was\" price) must be higher than the selling price.");
        }
    }

    private PageResponse<ProductDto> page(Specification<Product> base, String q, String categorySlug, String sort,
                                          String priceField, int page, int size,
                                          java.util.function.Function<Product, ProductDto> toDto, boolean families) {
        Specification<Product> spec = base;
        String query = Text.clean(q);
        boolean relevance = query != null && (sort == null || "relevance".equalsIgnoreCase(sort));
        if (query != null) spec = spec.and(matching(query, relevance, families));
        String slug = Text.clean(categorySlug);
        if (slug != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("category").get("slug"), slug));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48),
                relevance ? Sort.unsorted() : sortFor(sort, priceField)); // relevance orders inside matching()
        return PageResponse.of(products.findAll(spec, pageable), toDto);
    }

    /** The words of a search, lower-cased and de-duplicated (at most 8, so a pasted paragraph can't build a huge query). */
    static List<String> words(String query) {
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isBlank()).distinct().limit(8).toList();
    }

    /**
     * Every word of the search appears in the name, description or category (so "wireless headphones" finds
     * "Headphones, wireless"). With {@code rank}, best matches come first: the whole phrase in the name, then every
     * word in the name, then the rest, most-reviewed first within each. A variation's options count too ("red"), and
     * with {@code families} (where search shows one card per family) so do its sibling variations' names and options.
     */
    private static Specification<Product> matching(String query, boolean rank, boolean families) {
        List<String> words = words(query);
        String phrase = Text.contains(query);
        return (root, cq, cb) -> {
            var category = root.join("category", JoinType.LEFT);
            var name = cb.lower(root.get("name"));
            var text = cb.lower(cb.coalesce(root.<String>get("description"), ""));
            var categoryName = cb.lower(cb.coalesce(category.<String>get("name"), ""));
            List<jakarta.persistence.criteria.Predicate> all = new ArrayList<>();
            List<jakarta.persistence.criteria.Predicate> inName = new ArrayList<>();
            for (String w : words) {
                String p = Text.contains(w);
                var variation = cb.lower(cb.coalesce(root.<String>get("variation"), ""));
                var either = cb.or(cb.like(name, p), cb.like(text, p), cb.like(categoryName, p), cb.like(variation, p));
                if (families) {
                    // Families with a matching variation, found once per word (not once per product).
                    var matchingFamilies = cq.subquery(Long.class);
                    var m = matchingFamilies.from(Product.class);
                    matchingFamilies.select(m.get("familyId")).where(cb.isNotNull(m.get("familyId")),
                            cb.or(cb.like(cb.lower(m.get("name")), p),
                                    cb.like(cb.lower(cb.coalesce(m.<String>get("variation"), "")), p)));
                    either = cb.or(either, root.get("familyId").in(matchingFamilies));
                }
                all.add(either);
                inName.add(cb.like(name, p));
            }
            if (words.isEmpty()) all.add(cb.or(cb.like(name, phrase), cb.like(text, phrase)));
            // Count queries use the same predicate; only the page query gets the ranking.
            if (rank && cq.getResultType() != Long.class && cq.getResultType() != long.class) {
                var score = cb.selectCase()
                        .when(cb.like(name, phrase), 0)
                        .when(inName.isEmpty() ? cb.disjunction() : cb.and(inName.toArray(jakarta.persistence.criteria.Predicate[]::new)), 1)
                        .otherwise(2);
                cq.orderBy(cb.asc(score), cb.desc(root.get("ratingCount")), cb.asc(root.get("id")));
            }
            return cb.and(all.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static Sort sortFor(String sort, String priceField) {
        String s = sort == null ? "newest" : sort.toLowerCase(Locale.ROOT);
        List<Sort.Order> orders = new ArrayList<>();
        switch (s) {
            case "price_asc" -> orders.add(Sort.Order.asc(priceField));
            case "price_desc" -> orders.add(Sort.Order.desc(priceField));
            case "rating" -> {
                orders.add(Sort.Order.desc("ratingAvg"));
                orders.add(Sort.Order.desc("ratingCount"));
            }
            case "popular" -> { // most reviewed first: the closest thing we have to "best sellers"
                orders.add(Sort.Order.desc("ratingCount"));
                orders.add(Sort.Order.desc("ratingAvg"));
            }
            case "discount" -> orders.add(Sort.Order.desc("discountPercent"));
            case "name" -> orders.add(Sort.Order.asc("name"));
            default -> orders.add(Sort.Order.desc("createdAt"));
        }
        orders.add(Sort.Order.asc("id")); // stable paging
        return Sort.by(orders);
    }

    /** Finds a product; when {@code sellerId} is given it must belong to that seller (else it "doesn't exist"). */
    private Product find(Long id, Long sellerId) {
        Product p = products.findWithCategoryById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        if (sellerId != null && (p.getSeller() == null || !p.getSeller().getId().equals(sellerId))) {
            throw ApiException.notFound("Product not found.");
        }
        return p;
    }

    private Category category(Long id) {
        if (id == null) return null;
        return categories.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown category."));
    }

    private void checkNameFree(String name, Category self) {
        boolean taken = categories.findAll().stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(name) && (self == null || !c.getId().equals(self.getId())));
        if (taken) throw ApiException.conflict("A category with that name already exists.");
    }

    private String uniqueSlug(String name, Category self) {
        String base = Text.slugify(name);
        String slug = base;
        int n = 2;
        while (true) {
            var existing = categories.findBySlug(slug);
            if (existing.isEmpty() || (self != null && existing.get().getId().equals(self.getId()))) return slug;
            slug = base + "-" + n++;
        }
    }
}
