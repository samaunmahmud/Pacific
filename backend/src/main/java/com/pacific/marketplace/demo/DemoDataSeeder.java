package com.pacific.marketplace.demo;

import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.ItemCondition;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.domain.Setting;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.SettingRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.service.BuyBox;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills a development database with a realistic-looking shop: ~150 products in 13 categories from 8 stores, 41
 * shoppers and a few thousand reviews. Off unless {@code app.demo-data.enabled=true} (env DEMO_DATA=true).
 *
 * <p>It only adds data (your own products, users and orders are left alone) and runs at most once per database: a
 * marker setting records that it has. Everything is invented and generated from a fixed random seed, so two fresh
 * databases get the same shop. All the demo accounts have unusable passwords except the one shopper below.
 * Never enable this against a real shop.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String MARKER = "demo_data";
    /** Added separately, so demo databases made before multi-seller offers get some too. */
    static final String OFFERS_MARKER = "demo_offers";
    /** The one demo account you can sign in with. */
    public static final String SHOPPER_EMAIL = "demo.shopper@example.com";
    public static final String SHOPPER_PASSWORD = "Demo-Pacific-123";

    private static final int SHOPPERS = 40;

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final UserRepository users;
    private final SellerProfileRepository sellers;
    private final ReviewRepository reviews;
    private final SettingRepository settings;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final BuyBox buyBox;

    public DemoDataSeeder(CategoryRepository categories, ProductRepository products, UserRepository users,
                          SellerProfileRepository sellers, ReviewRepository reviews, SettingRepository settings,
                          PasswordEncoder encoder, PlatformTransactionManager txManager, BuyBox buyBox) {
        this.categories = categories;
        this.products = products;
        this.users = users;
        this.sellers = sellers;
        this.reviews = reviews;
        this.settings = settings;
        this.encoder = encoder;
        this.tx = new TransactionTemplate(txManager);
        this.buyBox = buyBox;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.existsById(MARKER)) {
            log.info("Demo data was already added to this database; leaving it as it is.");
        } else {
            seedShop();
        }
        if (!settings.existsById(OFFERS_MARKER)) {
            Integer offers = tx.execute(status -> seedOffers());
            settings.save(new Setting(OFFERS_MARKER, "v1"));
            log.warn("DEMO DATA: {} offers from other stores added to existing products.", offers);
        }
    }

    private void seedShop() {
        long start = System.currentTimeMillis();
        int[] counts = tx.execute(status -> seed());
        settings.save(new Setting(MARKER, "v1"));
        log.warn("DEMO DATA added: {} products, {} reviews, {} shoppers, {} stores in {} ms. Sign in as {} / {}. "
                        + "Never enable this against a real shop.", counts[0], counts[1], SHOPPERS + 1, counts[2],
                System.currentTimeMillis() - start, SHOPPER_EMAIL, SHOPPER_PASSWORD);
    }

    /**
     * Other demo stores start selling about one product in five: at a slightly different price, sometimes sold out,
     * sometimes used, so product pages show buy boxes and "Other sellers". Returns how many offers were added.
     */
    private int seedOffers() {
        Random rnd = new Random(20260926L);
        List<SellerProfile> stores = DemoCatalog.STORES.stream()
                .map(st -> sellers.findBySlug(slugify(st.name())).orElse(null)).filter(st -> st != null).toList();
        if (stores.size() < 2) return 0;
        Set<Long> demoStoreIds = stores.stream().map(SellerProfile::getId).collect(Collectors.toSet());
        List<Product> pages = products.findAll(Sort.by("id")).stream()
                .filter(p -> !p.isOffer() && p.isActive() && p.getImageUrl() != null && p.getImageUrl().startsWith("demo:")
                        && (p.getSeller() == null || demoStoreIds.contains(p.getSeller().getId())))
                .toList();
        int added = 0;
        for (int i = 0; i < pages.size(); i += 5) {
            Product page = pages.get(i);
            List<SellerProfile> others = new ArrayList<>(stores.stream()
                    .filter(st -> page.getSeller() == null || !st.getId().equals(page.getSeller().getId())).toList());
            Collections.shuffle(others, rnd);
            int count = 1 + rnd.nextInt(2);
            for (int j = 0; j < count && j < others.size(); j++) {
                boolean used = rnd.nextInt(100) < 20;
                double factor = used ? 0.62 + rnd.nextDouble() * 0.15 : 0.9 + rnd.nextDouble() * 0.22;
                BigDecimal price = page.getPrice().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP)
                        .max(new BigDecimal("0.99"));
                int stock = rnd.nextInt(100) < 15 ? 0 : 2 + rnd.nextInt(40);
                Product offer = new Product(page.getName(), page.getDescription(), price, stock, page.getImageUrl(), page.getCategory());
                offer.copyCatalogDetails(page);
                offer.setGroupId(page.getId());
                offer.setSeller(others.get(j));
                offer.setCondition(used ? (rnd.nextBoolean() ? ItemCondition.USED_LIKE_NEW : ItemCondition.USED_GOOD) : ItemCondition.NEW);
                offer.setCreatedAt(page.getCreatedAt().plus(Duration.ofDays(1 + rnd.nextInt(20))));
                products.save(offer);
                added++;
            }
            buyBox.refresh(page.getId());
        }
        return added;
    }

    /** Returns {products, reviews, stores}. */
    private int[] seed() {
        Random rnd = new Random(20260919L);
        Instant now = Instant.now();
        String unusable = encoder.encode(UUID.randomUUID().toString());

        Map<String, Category> cats = new HashMap<>();
        categories.findAll().forEach(c -> cats.put(c.getName().toLowerCase(Locale.ROOT), c));

        Map<String, SellerProfile> stores = new HashMap<>();
        for (DemoCatalog.Store s : DemoCatalog.STORES) {
            String slug = slugify(s.name());
            User owner = users.save(confirmed(new User(s.name(), "seller." + slug + "@example.com", null, unusable, Role.CUSTOMER)));
            SellerProfile profile = new SellerProfile(owner, s.name(), slug, s.description());
            profile.setStatus(SellerStatus.APPROVED, null);
            profile.setRating(BigDecimal.valueOf(4.0 + rnd.nextInt(9) / 10.0).setScale(2), 20 + rnd.nextInt(280));
            stores.put(s.name(), sellers.save(profile));
        }

        List<User> shoppers = new ArrayList<>();
        shoppers.add(users.save(confirmed(new User("Demo Shopper", SHOPPER_EMAIL, null, encoder.encode(SHOPPER_PASSWORD), Role.CUSTOMER))));
        for (int i = 0; i < SHOPPERS; i++) {
            String first = DemoCatalog.FIRST_NAMES.get(i % DemoCatalog.FIRST_NAMES.size());
            String last = DemoCatalog.LAST_NAMES.get((i * 7 + 3) % DemoCatalog.LAST_NAMES.size());
            shoppers.add(users.save(confirmed(new User(first + " " + last,
                    "demo." + first.toLowerCase(Locale.ROOT) + "." + last.toLowerCase(Locale.ROOT) + i + "@example.com",
                    null, unusable, Role.CUSTOMER))));
        }

        int productCount = 0;
        int reviewCount = 0;
        for (DemoCatalog.Category def : DemoCatalog.CATEGORIES) {
            Category category = cats.computeIfAbsent(def.name().toLowerCase(Locale.ROOT),
                    k -> categories.save(new Category(def.name(), slugify(def.name()))));
            SellerProfile store = stores.get(def.store());
            int index = 0;
            for (DemoCatalog.Type type : def.types()) {
                String brand = def.brands().get(index % def.brands().size());
                String series = def.series().get(index % def.series().size());
                boolean isBook = "Books".equals(def.name());
                String name = isBook ? type.label()
                        : brand + " " + series + " " + (100 + (index * 37 + productCount * 13) % 900) + " " + type.label();

                BigDecimal price = price(type, rnd);
                BigDecimal listPrice = rnd.nextInt(100) < 26 ? listPriceFor(price, rnd) : null;
                Product p = new Product(name, description(brand, type, isBook), price, stock(rnd),
                        "demo:" + type.art() + ":" + hue(name), category);
                p.update(p.getName(), p.getDescription(), price, listPrice, p.getStock(), p.getImageUrl(), category, true);
                // Every fifth listing is sold by Pacific itself, the rest by the category's store.
                p.setSeller(index % 5 == 0 ? null : store);
                long daysAgo = 3 + rnd.nextInt(150);
                p.setCreatedAt(now.minus(Duration.ofDays(daysAgo)));
                p = products.save(p);
                reviewCount += addReviews(p, type, shoppers, rnd, now, daysAgo);
                productCount++;
                index++;
            }
        }
        return new int[] {productCount, reviewCount, stores.size()};
    }

    private int addReviews(Product p, DemoCatalog.Type type, List<User> shoppers, Random rnd, Instant now, long daysAgo) {
        double quality = 3.4 + rnd.nextDouble() * 1.4;            // how good this product really is
        double popularity = rnd.nextDouble();
        int count = 3 + (int) (popularity * popularity * 36);      // most products have a few, some have many
        List<User> pool = new ArrayList<>(shoppers);
        Collections.shuffle(pool, rnd);
        String[] features = type.features().split("\\|");

        List<Review> made = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < count && i < pool.size(); i++) {
            int rating = (int) Math.max(1, Math.min(5, Math.round(quality + rnd.nextGaussian() * 0.85 + 0.15)));
            Review r = new Review(p, pool.get(i), rating, reviewTitle(rating, rnd),
                    reviewText(rating, type.label(), features[rnd.nextInt(features.length)], rnd), null);
            r.setCreatedAt(now.minus(Duration.ofHours(1 + (long) (rnd.nextDouble() * (daysAgo * 24 - 1)))));
            made.add(r);
            sum += rating;
        }
        reviews.saveAll(made);
        p.setRating(BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(made.size()), 2, RoundingMode.HALF_UP), made.size());
        return made.size();
    }

    // ---------- generated text and numbers ----------

    private static final String[][] TITLES = {
            {},
            {"Disappointed", "Not as described", "Would not recommend"},
            {"Not great", "Okay at best", "Expected more"},
            {"Does the job", "Decent for the price", "Fine, nothing special"},
            {"Really good", "Very happy with this", "Great value"},
            {"Absolutely love it", "Worth every penny", "Brilliant", "Exceeded my expectations", "Five stars"}};

    private static String reviewTitle(int rating, Random rnd) {
        String[] options = TITLES[rating];
        return options[rnd.nextInt(options.length)];
    }

    private static String reviewText(int rating, String label, String feature, Random rnd) {
        String item = label.replaceAll(" \\(.*\\)$", "").toLowerCase(Locale.ROOT);
        return switch (rating) {
            case 5 -> pick(rnd, "I've had this " + item + " for a few weeks and I'm delighted. " + feature + ". Would buy again.",
                    "Arrived quickly and works exactly as described. " + feature + ". Couldn't be happier.",
                    "Bought this as a replacement and it's a big step up. " + feature + ". Highly recommended.");
            case 4 -> pick(rnd, "Really good overall. " + feature + ". The packaging could be better but the product itself is solid.",
                    "Very pleased with this " + item + ". " + feature + ". Knocked off a star only because it took a while to arrive.");
            case 3 -> pick(rnd, "It does the job. " + feature + ", though I expected a little more for the price.",
                    "Average. Some things are good ( " + feature.toLowerCase(Locale.ROOT) + " ) but others feel a bit cheap.");
            case 2 -> pick(rnd, "Not quite what I hoped for. " + feature + " on paper, but in practice it let me down.",
                    "Felt cheaper than the photos suggest. It works, but I wouldn't buy it again.");
            default -> pick(rnd, "Disappointed. Stopped working properly after a short while.",
                    "Not as described and hard to get support. I'm returning it.");
        };
    }

    private static String pick(Random rnd, String... options) {
        return options[rnd.nextInt(options.length)];
    }

    private static BigDecimal price(DemoCatalog.Type type, Random rnd) {
        int whole = type.minPrice() + rnd.nextInt(Math.max(1, type.maxPrice() - type.minPrice() + 1));
        double fraction = rnd.nextBoolean() ? 0.99 : (rnd.nextInt(3) == 0 ? 0.49 : 0.95);
        return BigDecimal.valueOf(Math.max(1, whole - 1) + fraction).setScale(2, RoundingMode.HALF_UP);
    }

    /** A believable "was" price, 12-45% above the selling price. */
    private static BigDecimal listPriceFor(BigDecimal price, Random rnd) {
        double factor = 1.12 + rnd.nextDouble() * 0.33;
        BigDecimal list = price.multiply(BigDecimal.valueOf(factor)).setScale(0, RoundingMode.HALF_UP)
                .subtract(BigDecimal.valueOf(0.01)).setScale(2, RoundingMode.HALF_UP);
        return list.compareTo(price) > 0 ? list : price.add(BigDecimal.ONE);
    }

    private static int stock(Random rnd) {
        int roll = rnd.nextInt(100);
        if (roll < 3) return 0;
        if (roll < 10) return 1 + rnd.nextInt(5);
        return 20 + rnd.nextInt(380);
    }

    private static String description(String brand, DemoCatalog.Type type, boolean isBook) {
        StringBuilder text = new StringBuilder();
        for (String feature : type.features().split("\\|")) text.append(feature).append('\n');
        text.append(isBook ? "Published by " + brand + "." : "Made by " + brand + " and backed by a 12-month guarantee.");
        return text.toString();
    }

    /** A stable colour for a product's drawn image. */
    private static int hue(String name) {
        return Math.floorMod(name.hashCode() * 31, 360);
    }

    private static String slugify(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("&", "and").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Demo accounts use made-up example.com addresses, so they start out confirmed. */
    private static User confirmed(User user) {
        user.markEmailVerified();
        return user;
    }
}
