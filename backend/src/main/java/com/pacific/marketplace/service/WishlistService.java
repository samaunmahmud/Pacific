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

    public WishlistService(WishlistItemRepository wishlist, ProductRepository products, UserRepository users) {
        this.wishlist = wishlist;
        this.products = products;
        this.users = users;
    }

    /** Saved products that can still be bought (hidden or suspended-seller products are left out). */
    @Transactional(readOnly = true)
    public List<ProductDto> list(Long userId) {
        return wishlist.findProductsForUser(userId).stream().filter(Product::isVisibleInStore)
                .map(ProductDto::from).toList();
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
