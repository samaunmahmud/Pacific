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

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
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

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, String categorySlug, String sellerSlug, boolean dealsOnly,
                                           String sort, int page, int size) {
        Specification<Product> spec = storefront();
        String seller = Text.clean(sellerSlug);
        if (seller != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("seller").get("slug"), seller));
        if (dealsOnly) spec = spec.and((root, cq, cb) -> cb.greaterThan(root.<Integer>get("discountPercent"), 0));
        return page(spec, q, categorySlug, sort, page, size);
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id) {
        return products.findWithCategoryById(id).filter(Product::isVisibleInStore).map(ProductDto::from)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    /** For the "recently viewed" strip: the visible products among {@code ids}, in the order requested. */
    @Transactional(readOnly = true)
    public List<ProductDto> batch(List<Long> ids) {
        List<Long> wanted = ids.stream().distinct().limit(20).toList();
        Map<Long, Product> byId = new HashMap<>();
        products.findWithCategoryByIdIn(wanted).stream().filter(Product::isVisibleInStore)
                .forEach(p -> byId.put(p.getId(), p));
        return wanted.stream().map(byId::get).filter(p -> p != null).map(ProductDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderByNameAsc().stream().map(CategoryDto::from).toList();
    }

    // ---------- admin: products (Pacific's own catalogue, but admins can manage any listing) ----------

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> adminSearch(String q, String categorySlug, String sort, int page, int size) {
        return page((root, cq, cb) -> cb.conjunction(), q, categorySlug, sort, page, size);
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
        return page((root, cq, cb) -> cb.equal(root.get("seller").get("id"), sellerId), q, null, "newest", page, size);
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
        Product p = new Product(req.name().strip(), Text.clean(req.description()), req.price(), req.stock(),
                Text.clean(req.imageUrl()), category(req.categoryId()));
        p.setSeller(seller);
        p.update(p.getName(), p.getDescription(), req.price(), req.listPrice(), req.stock(), p.getImageUrl(),
                p.getCategory(), !Boolean.FALSE.equals(req.active()));
        return ProductDto.from(products.save(p));
    }

    private ProductDto updateFor(Long id, ProductRequest req, Long sellerId) {
        checkPrices(req);
        Product p = find(id, sellerId);
        boolean active = req.active() == null ? p.isActive() : req.active();
        p.update(req.name().strip(), Text.clean(req.description()), req.price(), req.listPrice(), req.stock(),
                Text.clean(req.imageUrl()), category(req.categoryId()), active);
        return ProductDto.from(p);
    }

    private ProductDto stockFor(Long id, int stock, Long sellerId) {
        Product p = find(id, sellerId);
        p.setStock(stock);
        return ProductDto.from(p);
    }

    private ProductDto deactivateFor(Long id, Long sellerId) {
        Product p = find(id, sellerId);
        p.setActive(false);
        return ProductDto.from(p);
    }

    private static void checkPrices(ProductRequest req) {
        BigDecimal list = req.listPrice();
        if (list != null && list.compareTo(req.price()) <= 0) {
            throw ApiException.badRequest("The list price (the \"was\" price) must be higher than the selling price.");
        }
    }

    private PageResponse<ProductDto> page(Specification<Product> base, String q, String categorySlug, String sort,
                                          int page, int size) {
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
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 48), sortFor(sort));
        return PageResponse.of(products.findAll(spec, pageable), ProductDto::from);
    }

    private static Sort sortFor(String sort) {
        String s = sort == null ? "newest" : sort.toLowerCase(Locale.ROOT);
        List<Sort.Order> orders = new ArrayList<>();
        switch (s) {
            case "price_asc" -> orders.add(Sort.Order.asc("price"));
            case "price_desc" -> orders.add(Sort.Order.desc("price"));
            case "rating" -> {
                orders.add(Sort.Order.desc("ratingAvg"));
                orders.add(Sort.Order.desc("ratingCount"));
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
