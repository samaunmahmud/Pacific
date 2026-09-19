package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.CartItem;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.CartItemRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.CartDtos.CartDto;
import com.pacific.marketplace.web.dto.CartDtos.CartItemDto;
import com.pacific.marketplace.web.dto.CartDtos.ShipmentDto;
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

    public CartService(CartItemRepository cart, ProductRepository products, UserRepository users,
                       ShopPricing pricing) {
        this.cart = cart;
        this.products = products;
        this.users = users;
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        return toDto(cart.findAllForUser(userId));
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

    @Transactional
    public CartDto remove(Long userId, Long productId) {
        cart.findByUserIdAndProductId(userId, productId).ifPresent(cart::delete);
        cart.flush();
        return get(userId);
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

    private CartDto toDto(List<CartItem> items) {
        List<CartItemDto> lines = items.stream().map(i -> {
            Product p = i.getProduct();
            var seller = p.getSeller();
            return new CartItemDto(p.getId(), p.getName(), p.getImageUrl(),
                    p.getCategory() == null ? null : p.getCategory().getName(), p.getPrice(), i.getQuantity(),
                    p.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())), p.getStock(),
                    seller == null ? "Pacific" : seller.getStoreName(), seller == null ? null : seller.getSlug());
        }).toList();

        // Each seller ships separately, so shipping (and the free-shipping threshold) applies per seller.
        Map<String, BigDecimal> subtotalBySeller = new LinkedHashMap<>();
        Map<String, String> slugBySeller = new LinkedHashMap<>();
        for (CartItemDto l : lines) {
            subtotalBySeller.merge(l.sellerName(), l.lineTotal(), BigDecimal::add);
            slugBySeller.putIfAbsent(l.sellerName(), l.sellerSlug());
        }
        List<ShipmentDto> shipments = subtotalBySeller.entrySet().stream().map(e -> {
            BigDecimal sub = e.getValue().setScale(2);
            return new ShipmentDto(e.getKey(), slugBySeller.get(e.getKey()), sub, pricing.shippingFor(sub));
        }).toList();

        BigDecimal subtotal = shipments.stream().map(ShipmentDto::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        BigDecimal shipping = shipments.stream().map(ShipmentDto::shipping).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        return new CartDto(lines, shipments, lines.stream().mapToInt(CartItemDto::quantity).sum(), subtotal,
                shipping, subtotal.add(shipping), pricing.freeShippingThreshold());
    }
}
