package com.pacific.marketplace.legacy;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.service.ReviewService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-off import of the old JavaFX app's SQLite database (DataBase1.db) into the new schema.
 * Runs only when app.legacy-import.sqlite-path (env LEGACY_SQLITE_PATH) is set, and only into an empty database.
 * The old app stored plaintext passwords in a column named password_hash; they are BCrypt-hashed here.
 */
@Component
@Order(1)
public class LegacyDataImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataImporter.class);

    /** Legacy products had no categories, so these are assigned by name. */
    private static final Map<String, String> CATEGORY_BY_PRODUCT = new HashMap<>();

    static {
        put("Audio", "Wireless Headphones", "Bluetooth Speaker", "Earbuds", "Microphone");
        put("Computer Accessories", "Gaming Mouse", "Mechanical Keyboard", "USB-C Hub", "Webcam HD", "External SSD");
        put("Cables & Power", "HDMI Cable", "Charging Cable", "Power Bank");
        put("PC Components", "CPU Cooler", "PC Case");
        put("Home Office", "Monitor Stand", "Desk Mat", "Laptop Stand", "Office Chair", "Desk Lamp");
        put("Photo & Video", "Ring Light", "Phone Tripod");
        put("Lifestyle", "Smart Watch Pro", "Eco-Friendly Water Bottle", "Leather Wallet");
    }

    private static void put(String category, String... products) {
        for (String p : products) CATEGORY_BY_PRODUCT.put(p, category);
    }

    private final AppProperties props;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ReviewRepository reviews;
    private final ReviewService reviewService;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public LegacyDataImporter(AppProperties props, UserRepository users, CategoryRepository categories,
                              ProductRepository products, ReviewRepository reviews, ReviewService reviewService,
                              PasswordEncoder encoder, TransactionTemplate tx) {
        this.props = props;
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.reviews = reviews;
        this.reviewService = reviewService;
        this.encoder = encoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String path = props.legacyImport() == null ? null : props.legacyImport().sqlitePath();
        if (path == null || path.isBlank()) return;

        if (!Files.isRegularFile(Path.of(path))) {
            throw new IllegalStateException("Legacy import: SQLite file not found at " + path);
        }
        if (users.count() > 0 || products.count() > 0) {
            log.warn("Legacy import skipped: the database already contains data.");
            return;
        }
        // open_mode=1 opens the old database read-only so it can't be modified by mistake
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + path + "?open_mode=1")) {
            tx.executeWithoutResult(status -> {
                try {
                    importAll(sqlite);
                } catch (SQLException e) {
                    throw new IllegalStateException("Legacy import failed: " + e.getMessage(), e);
                }
            });
        }
    }

    private void importAll(Connection sqlite) throws SQLException {
        Map<String, Category> categoryByName = new LinkedHashMap<>();
        for (String name : new java.util.TreeSet<>(CATEGORY_BY_PRODUCT.values())) {
            categoryByName.put(name, categories.save(new Category(name, name.toLowerCase(Locale.ROOT)
                    .replace("&", "and").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""))));
        }

        Map<Long, Product> productById = new HashMap<>();
        try (Statement st = sqlite.createStatement();
             ResultSet rs = st.executeQuery("SELECT product_id, name, description, price, created_at FROM product")) {
            while (rs.next()) {
                String name = rs.getString("name");
                Product p = new Product(name, rs.getString("description"), BigDecimal.valueOf(rs.getDouble("price"))
                        .setScale(2, java.math.RoundingMode.HALF_UP), props.legacyImport().defaultStock(), null,
                        categoryByName.get(CATEGORY_BY_PRODUCT.get(name)));
                Instant created = parse(rs.getString("created_at"));
                if (created != null) p.setCreatedAt(created);
                productById.put(rs.getLong("product_id"), products.save(p));
            }
        }

        Map<Long, User> customerById = new HashMap<>();
        Set<String> seenEmails = new HashSet<>();
        try (Statement st = sqlite.createStatement();
             ResultSet rs = st.executeQuery("SELECT customer_id, name, email, password_hash, created_at FROM customer")) {
            while (rs.next()) {
                String email = rs.getString("email").strip().toLowerCase(Locale.ROOT);
                if (!seenEmails.add(email)) {
                    log.warn("Legacy import: skipping customer {} (duplicate email {})", rs.getLong("customer_id"), email);
                    continue;
                }
                User u;
                try {
                    u = new User(rs.getString("name"), email, null, encoder.encode(rs.getString("password_hash")),
                            Role.CUSTOMER);
                } catch (IllegalArgumentException e) {
                    log.warn("Legacy import: skipping customer {} (password can't be hashed)", rs.getLong("customer_id"));
                    continue;
                }
                Instant created = parse(rs.getString("created_at"));
                if (created != null) u.setCreatedAt(created);
                customerById.put(rs.getLong("customer_id"), users.save(u));
            }
        }

        int admins = 0;
        try (Statement st = sqlite.createStatement();
             ResultSet rs = st.executeQuery("SELECT username, password_hash FROM admin")) {
            while (rs.next()) {
                String username = rs.getString("username").strip();
                users.save(new User(username, null, username, encoder.encode(rs.getString("password_hash")),
                        Role.ADMIN));
                admins++;
            }
        }

        int imported = 0;
        try (Statement st = sqlite.createStatement();
             ResultSet rs = st.executeQuery("SELECT product_id, customer_id, rating, comment, image_url, review_status, "
                     + "helpful_count, unhelpful_count, created_at FROM review")) {
            while (rs.next()) {
                Product product = productById.get(rs.getLong("product_id"));
                User customer = customerById.get(rs.getLong("customer_id"));
                if (product == null || customer == null) continue;

                String comment = rs.getString("comment");
                String title = null;
                // The old app stored "Title: body" in one column.
                int split = comment.indexOf(": ");
                if (split > 0 && split <= 120 && split + 2 < comment.length()) {
                    title = comment.substring(0, split).strip();
                    comment = comment.substring(split + 2);
                }
                if (comment.length() > 1000) comment = comment.substring(0, 1000);
                String image = rs.getString("image_url");
                if (image == null || !image.matches("^https?://\\S+$")) image = null; // old values were local file paths

                Review r = new Review(product, customer, rs.getInt("rating"), title, comment.strip(), image);
                if ("flagged".equalsIgnoreCase(rs.getString("review_status"))) r.setStatus(ReviewStatus.FLAGGED);
                r.setHelpfulCount(Math.max(0, rs.getInt("helpful_count")));
                r.setUnhelpfulCount(Math.max(0, rs.getInt("unhelpful_count")));
                Instant created = parse(rs.getString("created_at"));
                if (created != null) r.setCreatedAt(created);
                reviews.save(r);
                imported++;
            }
        }
        reviews.flush();
        productById.values().forEach(reviewService::recalculateRating);

        log.info("Legacy import complete: {} categories, {} products, {} customers, {} admins, {} reviews.",
                categoryByName.size(), productById.size(), customerById.size(), admins, imported);
    }

    /** SQLite's datetime('now') is UTC, formatted "yyyy-MM-dd HH:mm:ss". */
    private static Instant parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.strip().replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
