package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.WishlistItem;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.repo.WishlistItemRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlist;
    private final ProductRepository products;
    private final UserRepository users;
    private final ProductService catalog;
    private final BuyBox buyBox;

    public WishlistService(WishlistItemRepository wishlist, ProductRepository products, UserRepository users,
                           ProductService catalog, BuyBox buyBox) {
        this.wishlist = wishlist;
        this.products = products;
        this.users = users;
        this.catalog = catalog;
        this.buyBox = buyBox;
    }

    /** Saved products that some seller still sells (the buy box may be another seller's listing). */
    @Transactional(readOnly = true)
    public List<ProductDto> list(Long userId) {
        return wishlist.findProductsForUser(userId).stream()
                .filter(p -> p.isOffer() ? p.isVisibleInStore() : buyBox.catalogVisible(p.getId()))
                .map(catalog::card).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> ids(Long userId) {
        return wishlist.findProductIdsForUser(userId);
    }

    /** Idempotent: saving something already saved is fine. */
    @Transactional
    public void add(Long userId, Long productId) {
        Product product = products.findById(productId).filter(Product::isVisibleInStore)
                .orElseThrow(() -> ApiException.notFound("Product not found."));
        if (wishlist.findByUserIdAndProductId(userId, productId).isEmpty()) {
            wishlist.saveAndFlush(new WishlistItem(users.getReferenceById(userId), product));
        }
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        wishlist.findByUserIdAndProductId(userId, productId).ifPresent(wishlist::delete);
    }
}
