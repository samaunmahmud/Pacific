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

    public ProductService(ProductRepository products, CategoryRepository categories, BuyBox buyBox) {
        this.products = products;
        this.categories = categories;
        this.buyBox = buyBox;
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
     * is on sale (so a product stays in search when its first seller runs out but another still has it).
     */
    private static Specification<Product> catalog() {
        return (root, cq, cb) -> {
            var others = cq.subquery(Long.class);
            var o = others.from(Product.class);
            others.select(o.get("id")).where(cb.equal(o.get("groupId"), root.get("id")), onSale(o, cb));
            return cb.and(cb.isNull(root.get("groupId")), cb.or(onSale(root, cb), cb.exists(others)));
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
        if (dealsOnly) spec = spec.and((root, cq, cb) -> cb.greaterThan(root.<Integer>get("discountPercent"), 0));
        return page(spec, q, categorySlug, sort, priceField, page, size, this::card);
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        // An offer's id opens the product's catalog page, which is what shoppers see.
        Long catalogId = products.findById(id).map(Product::catalogId).orElse(id);
        if (!buyBox.catalogVisible(catalogId)) throw ApiException.notFound("Product not found.");
        return products.findWithCategoryById(catalogId).map(this::card)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    /** For the "recently viewed" strip: the visible products among {@code ids}, in the order requested. */
    @Transactional(readOnly = true)
    public List<ProductDto> batch(List<Long> ids) {
        List<Long> wanted = ids.stream().distinct().limit(20).toList();
        Map<Long, Product> byId = new HashMap<>();
        products.findWithCategoryByIdIn(wanted).stream().filter(p -> !p.isOffer() && buyBox.catalogVisible(p.getId()))
                .forEach(p -> byId.put(p.getId(), p));
        return wanted.stream().map(byId::get).filter(p -> p != null).map(this::card).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryDto::from).toList();
    }

    // ---------- admin: products (Pacific's own catalogue, but admins can manage any listing) ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> adminSearch(String q, String categorySlug, String sort, int page, int size) {
        return page((root, cq, cb) -> cb.conjunction(), q, categorySlug, sort, "price", page, size, ProductDto::from);
    }

    @Transactional(readOnly = true)
    public ProductDto adminGet(Long id) {
        return ProductDto.from(find(id, null));
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
                ProductDto::from);
    }

    @Transactional(readOnly = true)
    public ProductDto sellerGet(Long sellerId, Long id) {
        return ProductDto.from(find(id, sellerId));
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
                                          java.util.function.Function<Product, ProductDto> toDto) {
        Specification<Product> spec = base;
        String query = Text.clean(q);
        if (query != null) {
            String pattern = Text.contains(query);
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern)));
        }
        String slug = Text.clean(categorySlug);
        if (slug != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("category").get("slug"), slug));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), sortFor(sort, priceField));
        return PageResponse.of(products.findAll(spec, pageable), toDto);
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
