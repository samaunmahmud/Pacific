package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.ItemCondition;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which of a product's listings wins the buy box, and keeping the catalog page's copy of that price up to date.
 *
 * <p>The winner is the best listing shoppers can buy: in stock beats out of stock, new beats used, then the lowest
 * price, then the listing that came first. Search sorts and filters by the catalog page's {@code box_price}, so every
 * change to a listing's price, stock or visibility, or to a seller's status, calls {@link #refresh}.
 */
@Component
public class BuyBox {

    /** Best first. */
    public static final Comparator<Listing> ORDER = Comparator
            .comparing((Listing l) -> l.stock() <= 0)
            .thenComparing(l -> l.condition() != ItemCondition.NEW)
            .thenComparing(Listing::price)
            .thenComparing(Listing::id);

    /** A listing as read straight from the database (the stock updates bypass loaded entities). */
    public record Listing(Long id, BigDecimal price, int stock, ItemCondition condition, boolean active,
                          SellerStatus sellerStatus) {
        public boolean visible() {
            return active && (sellerStatus == null || sellerStatus == SellerStatus.APPROVED);
        }
    }

    @PersistenceContext
    private EntityManager em;

    /** Every listing on this catalog page, visible or not. */
    public List<Listing> listings(Long catalogId) {
        return em.createQuery("select new com.pacific.marketplace.service.BuyBox$Listing(p.id, p.price, p.stock, "
                        + "p.condition, p.active, s.status) from Product p left join p.seller s "
                        + "where p.id = :g or p.groupId = :g", Listing.class)
                .setParameter("g", catalogId).getResultList();
    }

    /** The listings shoppers can buy from, best first (the first one is the buy box). */
    public List<Listing> ranked(Long catalogId) {
        return listings(catalogId).stream().filter(Listing::visible).sorted(ORDER).toList();
    }

    /** Can shoppers see this catalog page? Yes while any of its listings is on sale. */
    public boolean catalogVisible(Long catalogId) {
        return listings(catalogId).stream().anyMatch(Listing::visible);
    }

    /** Re-picks the buy box for a catalog page and stores its price and the number of listings on sale. */
    @Transactional
    public void refresh(Long catalogId) {
        List<Listing> all = listings(catalogId);
        List<Listing> onSale = all.stream().filter(Listing::visible).sorted(ORDER).toList();
        BigDecimal price = onSale.isEmpty()
                ? all.stream().filter(l -> l.id().equals(catalogId)).map(Listing::price).findFirst().orElse(null)
                : onSale.get(0).price();
        if (price == null) return;
        em.createNativeQuery("update products set box_price = ?1, offer_count = ?2 where id = ?3")
                .setParameter(1, price).setParameter(2, Math.max(onSale.size(), 1)).setParameter(3, catalogId)
                .executeUpdate();
    }

    /** Refreshes the catalog pages these listings belong to. */
    @Transactional
    public void refreshFor(Collection<Long> productIds) {
        if (productIds.isEmpty()) return;
        catalogIdsOf(productIds).forEach(this::refresh);
    }

    /** Refreshes every catalog page this seller has a listing on (after their store is approved or suspended). */
    @Transactional
    public void refreshForSeller(Long sellerId) {
        em.createQuery("select distinct coalesce(p.groupId, p.id) from Product p where p.seller.id = :s", Long.class)
                .setParameter("s", sellerId).getResultList().forEach(this::refresh);
    }

    public List<Long> catalogIdsOf(Collection<Long> productIds) {
        return em.createQuery("select distinct coalesce(p.groupId, p.id) from Product p where p.id in :ids", Long.class)
                .setParameter("ids", productIds).getResultList();
    }

    /** For a listing already loaded. */
    public void refresh(Product listing) {
        refresh(listing.catalogId());
    }
}
