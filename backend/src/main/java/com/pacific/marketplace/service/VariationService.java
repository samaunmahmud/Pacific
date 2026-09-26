package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.ProductFamily;
import com.pacific.marketplace.repo.ProductFamilyRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.ProductDtos.FamilyRequest;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ProductDtos.VariationOption;
import com.pacific.marketplace.web.dto.ProductDtos.VariationRequest;
import com.pacific.marketplace.web.dto.ProductDtos.VariationsDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variations: the same item in other colours, sizes and so on. Each variation is its own catalog page (own price,
 * stock, photos, reviews and other sellers' offers); a family ties them together so search shows one card and the
 * product page offers a picker. The store that lists a product manages its variations (admins for Pacific's own).
 */
@Service
public class VariationService {

    public static final int MAX_VARIATIONS = 60;

    private final ProductRepository products;
    private final ProductFamilyRepository families;
    private final BuyBox buyBox;

    @PersistenceContext
    private EntityManager em;

    public VariationService(ProductRepository products, ProductFamilyRepository families, BuyBox buyBox) {
        this.products = products;
        this.families = families;
        this.buyBox = buyBox;
    }

    /** The picker shoppers see: variations on sale, each at its buy-box price. Null for a product without any. */
    @Transactional(readOnly = true)
    public VariationsDto forShoppers(Product page) {
        if (page.getFamilyId() == null) return null;
        ProductFamily family = families.findById(page.getFamilyId()).orElse(null);
        if (family == null) return null;
        List<VariationOption> options = new ArrayList<>();
        for (Product m : products.findByFamilyIdOrderByIdAsc(family.getId())) {
            List<BuyBox.Listing> ranked = buyBox.ranked(m.getId());
            if (ranked.isEmpty()) continue;
            BuyBox.Listing box = ranked.get(0);
            options.add(new VariationOption(m.getId(), m.getOption1(), m.getOption2(), box.price(), box.stock() > 0,
                    m.getImageUrl(), true));
        }
        return dto(family, page, options);
    }

    /** What Seller Central shows: every variation, hidden or not, with its own price and stock. */
    @Transactional(readOnly = true)
    public VariationsDto forOwner(Product page) {
        if (page.getFamilyId() == null) return null;
        ProductFamily family = families.findById(page.getFamilyId()).orElse(null);
        if (family == null) return null;
        List<VariationOption> options = products.findByFamilyIdOrderByIdAsc(family.getId()).stream()
                .map(m -> new VariationOption(m.getId(), m.getOption1(), m.getOption2(), m.getPrice(), m.getStock() > 0,
                        m.getImageUrl(), m.isActive()))
                .toList();
        return dto(family, page, options);
    }

    private static VariationsDto dto(ProductFamily f, Product page, List<VariationOption> options) {
        return new VariationsDto(f.getId(), f.getDim1(), f.getDim2(), page.getOption1(), page.getOption2(), options);
    }

    /**
     * For search cards: how many variations are on sale in each page's family (pages without a family are left
     * out). A variation is on sale while its own listing or another seller's offer for it is.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> countsOnSale(Collection<Long> pageIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (pageIds.isEmpty()) return out;
        em.createQuery("select p.id, count(m) from Product p, Product m left join m.seller ms "
                        + "where p.id in :ids and m.familyId = p.familyId and ((m.active = true and (ms is null or "
                        + "ms.status = com.pacific.marketplace.domain.SellerStatus.APPROVED)) or exists (select o.id "
                        + "from Product o left join o.seller os where o.groupId = m.id and o.active = true and (os is null "
                        + "or os.status = com.pacific.marketplace.domain.SellerStatus.APPROVED))) group by p.id",
                        Object[].class)
                .setParameter("ids", pageIds).getResultList()
                .forEach(r -> out.put((Long) r[0], ((Number) r[1]).intValue()));
        return out;
    }

    /**
     * Starts variations with this product as the first one, or changes its options (and the family's dimension
     * names, which every variation shares).
     */
    @Transactional
    public ProductDto setFamily(Long productId, Long sellerId, FamilyRequest req) {
        Product p = own(productId, sellerId);
        String dim1 = Text.clean(req.dim1());
        String dim2 = Text.clean(req.dim2());
        if (dim1 == null) throw ApiException.badRequest("Say what the variations differ by, e.g. Colour.");
        if (dim2 != null && dim2.equalsIgnoreCase(dim1)) {
            throw ApiException.badRequest("The two things variations differ by need different names.");
        }
        ProductFamily family;
        if (p.getFamilyId() == null) {
            family = families.save(new ProductFamily(dim1, dim2));
        } else {
            family = families.findById(p.getFamilyId()).orElseThrow();
            boolean others = products.findByFamilyIdOrderByIdAsc(family.getId()).size() > 1;
            if (others && (dim2 == null) != (family.getDim2() == null)) {
                throw ApiException.badRequest("Variations can't gain or lose a dimension once there are several. "
                        + "Remove the others first, or keep " + (family.getDim2() == null ? "one." : "two."));
            }
            family.rename(dim1, dim2);
        }
        String[] options = options(family, req.option1(), req.option2());
        checkFree(family, p.getId(), options);
        p.setVariation(family, options[0], options[1]);
        relabel(family); // dimension names may have changed for every variation
        return ProductDto.from(p).withVariations(forOwner(p));
    }

    /** A new variation of this product: its options, price and stock; name, description, category and photos copied. */
    @Transactional
    public ProductDto addVariation(Long productId, Long sellerId, VariationRequest req) {
        Product source = own(productId, sellerId);
        if (source.getFamilyId() == null) {
            throw ApiException.badRequest("Say what this product's variations differ by first (e.g. Colour).");
        }
        ProductFamily family = families.findById(source.getFamilyId()).orElseThrow();
        if (products.findByFamilyIdOrderByIdAsc(family.getId()).size() >= MAX_VARIATIONS) {
            throw ApiException.badRequest("A product can have at most " + MAX_VARIATIONS + " variations.");
        }
        String[] options = options(family, req.option1(), req.option2());
        checkFree(family, null, options);
        String photo = Text.clean(req.imageUrl());
        Product v = new Product(source.getName(), source.getDescription(), req.price(), req.stock(),
                photo != null ? photo : source.getImageUrl(), source.getCategory());
        v.setSeller(source.getSeller());
        // A different photo is of a different colour, say, so the source's other photos wouldn't match it.
        if (photo == null) v.setMoreImages(source.getMoreImages());
        v.update(v.getName(), v.getDescription(), req.price(), null, req.stock(), v.getImageUrl(), v.getCategory(), true);
        v.setVariation(family, options[0], options[1]);
        v = products.saveAndFlush(v);
        buyBox.refresh(v.getId());
        return ProductDto.from(v).withVariations(forOwner(v));
    }

    /** Takes this product out of its variations. The last one left becomes a product on its own too. */
    @Transactional
    public ProductDto leave(Long productId, Long sellerId) {
        Product p = own(productId, sellerId);
        Long familyId = p.getFamilyId();
        if (familyId == null) return ProductDto.from(p);
        clear(p);
        List<Product> rest = products.findByFamilyIdOrderByIdAsc(familyId).stream()
                .filter(m -> !m.getId().equals(p.getId())).toList();
        if (rest.size() <= 1) {
            rest.forEach(this::clear);
            products.flush();
            families.deleteById(familyId);
        }
        return ProductDto.from(p);
    }

    private void clear(Product p) {
        p.setVariation(null, null, null);
        products.findByGroupId(p.getId()).forEach(o -> o.copyCatalogDetails(p));
    }

    /** Keeps every variation's label (and its offers' copies) in step with the family's dimension names. */
    private void relabel(ProductFamily family) {
        for (Product m : products.findByFamilyIdOrderByIdAsc(family.getId())) {
            m.setVariation(family, m.getOption1(), m.getOption2());
            products.findByGroupId(m.getId()).forEach(o -> o.copyCatalogDetails(m));
        }
    }

    private static String[] options(ProductFamily family, String option1, String option2) {
        String o1 = Text.clean(option1);
        String o2 = Text.clean(option2);
        if (o1 == null) throw ApiException.badRequest("Give the " + family.getDim1().toLowerCase(Locale.ROOT) + ".");
        if (family.getDim2() != null && o2 == null) {
            throw ApiException.badRequest("Give the " + family.getDim2().toLowerCase(Locale.ROOT) + ".");
        }
        return new String[] {o1, family.getDim2() == null ? null : o2};
    }

    /** No two variations may have the same options. */
    private void checkFree(ProductFamily family, Long self, String[] options) {
        for (Product m : products.findByFamilyIdOrderByIdAsc(family.getId())) {
            if (m.getId().equals(self)) continue;
            if (same(m.getOption1(), options[0]) && same(m.getOption2(), options[1])) {
                throw ApiException.conflict("There's already a variation " + family.label(options[0], options[1]) + ".");
            }
        }
    }

    private static boolean same(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }

    /** A catalog page the caller manages: a seller's own (sellerId), or any for admins (null). */
    private Product own(Long id, Long sellerId) {
        Product p = products.findWithCategoryById(id).orElseThrow(() -> ApiException.notFound("Product not found."));
        if (sellerId != null && (p.getSeller() == null || !p.getSeller().getId().equals(sellerId))) {
            throw ApiException.notFound("Product not found.");
        }
        if (p.isOffer()) {
            throw ApiException.badRequest("Variations belong to the product page. You sell this product as an offer on "
                    + "another store's page, so its variations are theirs.");
        }
        return p;
    }
}
