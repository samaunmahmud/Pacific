package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.ProductDtos.OfferDto;
import com.pacific.marketplace.web.dto.ProductDtos.OfferRequest;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Several sellers selling the same product. A seller joins an existing product page with their own price, stock and
 * condition (an "offer"), instead of creating a duplicate listing; shoppers then see the best offer in the buy box
 * and the rest under "Other sellers". See {@link BuyBox} for how the winner is picked.
 */
@Service
public class OfferService {

    private final ProductRepository products;
    private final SellerService sellers;
    private final BuyBox buyBox;

    public OfferService(ProductRepository products, SellerService sellers, BuyBox buyBox) {
        this.products = products;
        this.sellers = sellers;
        this.buyBox = buyBox;
    }

    /** The listings on sale for this product (any listing's id works), buy box first. */
    @Transactional(readOnly = true)
    public List<OfferDto> offers(Long productId) {
        Long catalogId = products.findById(productId).map(Product::catalogId)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        List<BuyBox.Listing> ranked = buyBox.ranked(catalogId);
        if (ranked.isEmpty()) throw ApiException.notFound("Product not found.");
        Map<Long, Product> byId = new HashMap<>();
        products.findWithCategoryByIdIn(ranked.stream().map(BuyBox.Listing::id).toList()).forEach(p -> byId.put(p.getId(), p));
        List<OfferDto> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            BuyBox.Listing l = ranked.get(i);
            Product p = byId.get(l.id());
            SellerProfile s = p.getSeller();
            out.add(new OfferDto(p.getId(), s == null ? ProductDto.HOUSE_STORE : s.getStoreName(), s == null ? null : s.getSlug(),
                    s == null ? 0 : s.getRatingAvg().doubleValue(), s == null ? 0 : s.getRatingCount(),
                    l.price(), p.getListPrice(), p.getDiscountPercent(), l.stock(), p.getCondition(), p.getCondition().label(),
                    i == 0 && l.stock() > 0));
        }
        return out;
    }

    /** An approved seller starts selling an existing product (any listing's id works). One listing per seller. */
    @Transactional
    public ProductDto addOffer(Long sellerUserId, Long productId, OfferRequest req) {
        SellerProfile seller = sellers.approved(sellerUserId);
        Product page = products.findById(productId).map(p -> p.isOffer() ? products.findById(p.catalogId()).orElse(null) : p)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        if (page == null || !buyBox.catalogVisible(page.getId())) throw ApiException.notFound("Product not found.");
        if ((page.getSeller() != null && page.getSeller().getId().equals(seller.getId()))
                || products.existsByGroupIdAndSellerId(page.getId(), seller.getId())) {
            throw ApiException.conflict("You already sell this product. Change your price or stock in Seller Central.");
        }
        if (req.listPrice() != null && req.listPrice().compareTo(req.price()) <= 0) {
            throw ApiException.badRequest("The list price (the \"was\" price) must be higher than the selling price.");
        }
        Product offer = new Product(page.getName(), page.getDescription(), req.price(), req.stock(), page.getImageUrl(),
                page.getCategory());
        offer.copyCatalogDetails(page);
        offer.setGroupId(page.getId());
        offer.setSeller(seller);
        offer.setCondition(req.condition());
        offer.update(offer.getName(), offer.getDescription(), req.price(), req.listPrice(), req.stock(), offer.getImageUrl(),
                offer.getCategory(), true);
        offer = products.save(offer);
        buyBox.refresh(page.getId());
        return ProductDto.from(offer);
    }
}
