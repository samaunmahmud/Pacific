package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.web.dto.ProductDtos.CategoryDto;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helping shoppers find things: search suggestions as they type, "Frequently bought together" and "Related products"
 * on product pages, and "Buy it again". All work on product pages (catalog pages), whichever seller a purchase was
 * from, and only show products on sale.
 */
@Service
public class Recommendations {

    /** An order that counts as a purchase: not cancelled, not awaiting a card payment. */
    private static final String PURCHASED = "o.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, "
            + "com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)";

    public record Suggestions(List<ProductDto> products, List<CategoryDto> categories) {
    }

    public record ForProduct(List<ProductDto> boughtTogether, List<ProductDto> related) {
    }

    @PersistenceContext
    private EntityManager em;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductService catalog;
    private final BuyBox buyBox;

    public Recommendations(ProductRepository products, CategoryRepository categories, ProductService catalog, BuyBox buyBox) {
        this.products = products;
        this.categories = categories;
        this.catalog = catalog;
        this.buyBox = buyBox;
    }

    /** As the shopper types: the best-matching products and categories. */
    @Transactional(readOnly = true)
    public Suggestions suggest(String q) {
        String query = Text.clean(q);
        if (query == null || query.length() < 2) return new Suggestions(List.of(), List.of());
        List<ProductDto> found = catalog.search(query, null, null, false, "relevance", 0, 6).items();
        String needle = query.toLowerCase(Locale.ROOT);
        List<CategoryDto> cats = categories.findAllByOrderByNameAsc().stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(needle)).limit(3).map(CategoryDto::from).toList();
        return new Suggestions(found, cats);
    }

    /**
     * For a product page: what else was in the same checkouts most often (from any seller), and popular products in
     * the same category.
     */
    @Transactional(readOnly = true)
    public ForProduct forProduct(Long productId) {
        Product page = products.findById(productId).map(p -> p.isOffer() ? products.findById(p.catalogId()).orElse(p) : p)
                .orElseThrow(() -> com.pacific.marketplace.web.ApiException.notFound("Product not found."));
        Long catalogId = page.getId();

        List<Long> together = em.createQuery("select coalesce(p2.groupId, p2.id) from OrderItem i1 join i1.order o "
                        + "join i1.product p1, OrderItem i2 join i2.order o2 join i2.product p2 "
                        + "where o2.checkoutRef = o.checkoutRef and (p1.id = :c or p1.groupId = :c) "
                        + "and coalesce(p2.groupId, p2.id) <> :c and " + PURCHASED
                        + " group by coalesce(p2.groupId, p2.id) order by count(distinct o.checkoutRef) desc, coalesce(p2.groupId, p2.id)",
                        Long.class)
                .setParameter("c", catalogId).setMaxResults(10).getResultList();
        List<ProductDto> boughtTogether = cards(together, 3);

        Set<Long> skip = new LinkedHashSet<>(together);
        skip.add(catalogId);
        List<Long> sameCategory = page.getCategory() == null ? List.of() : em.createQuery(
                        "select p.id from Product p where p.groupId is null and p.category.id = :cat and p.id not in :skip "
                                + "order by p.ratingCount desc, p.ratingAvg desc, p.id", Long.class)
                .setParameter("cat", page.getCategory().getId()).setParameter("skip", skip).setMaxResults(24).getResultList();
        return new ForProduct(boughtTogether, cards(sameCategory, 8));
    }

    /** Products the customer has had delivered, most recent first, that are still on sale. */
    @Transactional(readOnly = true)
    public List<ProductDto> buyAgain(Long userId) {
        List<Long> ids = em.createQuery("select coalesce(p.groupId, p.id) from OrderItem i join i.order o join i.product p "
                        + "where o.user.id = :u and o.status = com.pacific.marketplace.domain.OrderStatus.DELIVERED "
                        + "group by coalesce(p.groupId, p.id) order by max(o.createdAt) desc", Long.class)
                .setParameter("u", userId).setMaxResults(30).getResultList();
        return cards(ids, 12);
    }

    /** Cards for these product pages, in this order, keeping only those on sale, at most {@code limit}. */
    private List<ProductDto> cards(List<Long> catalogIds, int limit) {
        if (catalogIds.isEmpty()) return List.of();
        Map<Long, Product> byId = new HashMap<>();
        products.findWithCategoryByIdIn(catalogIds).forEach(p -> byId.put(p.getId(), p));
        List<ProductDto> out = new ArrayList<>();
        for (Long id : catalogIds) {
            Product p = byId.get(id);
            if (p == null || !buyBox.catalogVisible(id)) continue;
            out.add(catalog.card(p));
            if (out.size() == limit) break;
        }
        return catalog.decorate(out);
    }
}
