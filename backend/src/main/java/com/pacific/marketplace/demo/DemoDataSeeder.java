package com.pacific.marketplace.demo;

import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.ItemCondition;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.ProductFamily;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.domain.Setting;
import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductFamilyRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.SettingRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.service.BuyBox;
import com.pacific.marketplace.service.Promotions;
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
    /** Demo coupons and the WELCOME10 code, added once. */
    static final String PROMOTIONS_MARKER = "demo_promotions";
    /** Colours and sizes of some demo products, added once. */
    static final String VARIATIONS_MARKER = "demo_variations";
    /** The larger catalogue (app.demo-data.extra-products more products), added once. */
    static final String MORE_PRODUCTS_MARKER = "demo_more_products";
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
    private final Promotions promotions;
    private final ProductFamilyRepository families;
    private final int extraProducts;

    public DemoDataSeeder(CategoryRepository categories, ProductRepository products, UserRepository users,
                          SellerProfileRepository sellers, ReviewRepository reviews, SettingRepository settings,
                          PasswordEncoder encoder, PlatformTransactionManager txManager, BuyBox buyBox,
                          Promotions promotions, ProductFamilyRepository families,
                          @org.springframework.beans.factory.annotation.Value("${app.demo-data.extra-products:2000}") int extraProducts) {
        this.categories = categories;
        this.products = products;
        this.users = users;
        this.sellers = sellers;
        this.reviews = reviews;
        this.settings = settings;
        this.encoder = encoder;
        this.tx = new TransactionTemplate(txManager);
        this.buyBox = buyBox;
        this.promotions = promotions;
        this.families = families;
        this.extraProducts = extraProducts;
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
        if (!settings.existsById(PROMOTIONS_MARKER)) {
            Integer coupons = tx.execute(status -> seedCoupons());
            settings.save(new Setting(PROMOTIONS_MARKER, "v1"));
            log.warn("DEMO DATA: {} coupons and the promo code WELCOME10 (10% off Pacific's own products) added.", coupons);
        }
        if (!settings.existsById(VARIATIONS_MARKER)) {
            Integer added = tx.execute(status -> seedVariations());
            settings.save(new Setting(VARIATIONS_MARKER, "v1"));
            log.warn("DEMO DATA: {} colour and size variations added to demo products.", added);
        }
        if (extraProducts > 0 && !settings.existsById(MORE_PRODUCTS_MARKER)) {
            long start = System.currentTimeMillis();
            int[] added = seedMoreProducts(extraProducts);
            settings.save(new Setting(MORE_PRODUCTS_MARKER, "v1"));
            log.warn("DEMO DATA: {} more products ({} reviews, {} offers, {} variations) and {} more stores added in {} ms.",
                    added[0], added[1], added[2], added[3], added[4], System.currentTimeMillis() - start);
        }
        // Lightning Deals last hours, so a demo shop starts a fresh batch whenever none is running.
        Integer deals = tx.execute(status -> seedDeals());
        if (deals != null && deals > 0) log.warn("DEMO DATA: {} Lightning Deals started for the next 12 hours.", deals);
    }

    /** Demo listings: products from the demo stores and Pacific, on sale, with a drawing (so not your own). */
    private List<Product> demoListings() {
        Set<Long> demoStores = java.util.stream.Stream.concat(DemoCatalog.STORES.stream().map(DemoCatalog.Store::name),
                        DemoCatalog.EXTRA_STORES.stream().map(DemoCatalog.ExtraStore::name))
                .map(n -> sellers.findBySlug(slugify(n)).orElse(null))
                .filter(st -> st != null).map(SellerProfile::getId).collect(Collectors.toSet());
        return products.findAll(Sort.by("id")).stream()
                .filter(p -> p.isActive() && p.getStock() > 5 && p.getImageUrl() != null && p.getImageUrl().startsWith("demo:")
                        && (p.getSeller() == null || demoStores.contains(p.getSeller().getId())))
                .toList();
    }

    private int seedCoupons() {
        List<Product> listings = demoListings();
        int added = 0;
        for (int i = 3; i < listings.size() && added < 12; i += 11) {
            Product p = listings.get(i);
            promotions.createCoupon(p.getSeller() == null ? null : p.getSeller().getId(), p.getId(), 5 + (i % 4) * 5, 200, 90);
            added++;
        }
        promotions.createCode(null, "WELCOME10", 10, BigDecimal.ZERO, null, 90);
        return added;
    }

    /** Starts 8 Lightning Deals if none is running on a demo listing. */
    private int seedDeals() {
        List<Product> listings = demoListings();
        if (listings.isEmpty() || promotions.anyLiveDeal(listings.stream().map(Product::getId).toList())) return 0;
        Random rnd = new Random();
        List<Product> shuffled = new ArrayList<>(listings);
        Collections.shuffle(shuffled, rnd);
        int started = 0;
        for (Product p : shuffled) {
            if (started == 8) break;
            int percent = 15 + rnd.nextInt(26);
            BigDecimal price = p.getPrice().multiply(BigDecimal.valueOf(100 - percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            try {
                promotions.createDeal(p.getSeller() == null ? null : p.getSeller().getId(), p.getId(), price,
                        Math.min(p.getStock(), 5 + rnd.nextInt(20)), null, 12);
                started++;
            } catch (RuntimeException e) {
                // e.g. a deal scheduled there already: try the next listing
            }
        }
        return started;
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
        return addOffers(pages, stores, rnd, 5);
    }

    /** Offers from other stores on every {@code every}-th of these pages. Returns how many were added. */
    private int addOffers(List<Product> pages, List<SellerProfile> stores, Random rnd, int every) {
        int added = 0;
        for (int i = 0; i < pages.size(); i += every) {
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

    /** Named colours, as hues of the demo drawings. */
    private static final List<Map.Entry<String, Integer>> COLOURS = List.of(Map.entry("Red", 0), Map.entry("Orange", 28),
            Map.entry("Mustard", 48), Map.entry("Green", 130), Map.entry("Teal", 175), Map.entry("Blue", 215),
            Map.entry("Navy", 235), Map.entry("Purple", 275), Map.entry("Pink", 325));

    /**
     * Some demo products come in other colours (clothes in sizes too): each variation is its own product page, with
     * the drawing in its own colour. Returns how many variations were added.
     */
    private int seedVariations() {
        List<Product> pages = products.findAll(Sort.by("id")).stream()
                .filter(p -> !p.isOffer() && p.isActive() && p.getFamilyId() == null && p.getImageUrl() != null
                        && p.getImageUrl().matches("demo:[a-z-]+:\\d+") && p.getCategory() != null)
                .toList();
        return addVariations(pages, new Random(20260927L), 3, 2);
    }

    /**
     * Colours (and for clothes, sizes) for up to {@code sizedLimit} clothes and {@code otherLimit} other products of
     * each kind among these pages. Returns how many variations were added.
     */
    private int addVariations(List<Product> pages, Random rnd, int sizedLimit, int otherLimit) {
        int added = 0;
        Map<String, Integer> perArt = new HashMap<>();
        for (Product page : pages) {
            String[] image = page.getImageUrl().split(":");
            String art = image[1];
            boolean sized = art.equals("shirt") || art.equals("trousers");
            boolean coloured = sized || art.equals("shoe") || art.equals("beanie") || art.equals("scarf")
                    || art.equals("headphones") || art.equals("earbuds") || art.equals("speaker");
            if (!coloured || page.getFamilyId() != null || perArt.merge(art, 1, Integer::sum) > (sized ? sizedLimit : otherLimit)) continue;

            int hue = Integer.parseInt(image[2]);
            Map.Entry<String, Integer> own = COLOURS.stream()
                    .min(java.util.Comparator.comparingInt(c -> hueDistance(c.getValue(), hue))).orElseThrow();
            List<Map.Entry<String, Integer>> others = new ArrayList<>(COLOURS.stream().filter(c -> c != own).toList());
            Collections.shuffle(others, rnd);
            List<Map.Entry<String, Integer>> colours = new ArrayList<>(List.of(own, others.get(0), others.get(1)));
            List<String> sizes = sized ? List.of("S", "M", "L") : java.util.Collections.singletonList(null);

            ProductFamily family = families.save(new ProductFamily("Colour", sized ? "Size" : null));
            page.setVariation(family, own.getKey(), sized ? "M" : null);
            for (Map.Entry<String, Integer> colour : colours) {
                for (String size : sizes) {
                    if (colour == own && (size == null || size.equals("M"))) continue;
                    BigDecimal price = size != null && size.equals("L")
                            ? page.getPrice().add(new BigDecimal("2.00")) : page.getPrice();
                    int stock = rnd.nextInt(100) < 12 ? 0 : 3 + rnd.nextInt(30);
                    Product v = new Product(page.getName(), page.getDescription(), price, stock,
                            "demo:" + art + ":" + colour.getValue(), page.getCategory());
                    v.setSeller(page.getSeller());
                    v.update(v.getName(), v.getDescription(), price, null, stock, v.getImageUrl(), v.getCategory(), true);
                    v.setVariation(family, colour.getKey(), size);
                    v.setCreatedAt(page.getCreatedAt());
                    products.save(v);
                    added++;
                }
            }
            products.findByGroupId(page.getId()).forEach(o -> o.copyCatalogDetails(page));
        }
        return added;
    }

    private static int hueDistance(int a, int b) {
        int d = Math.abs(a - b) % 360;
        return Math.min(d, 360 - d);
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
        double popularity = rnd.nextDouble();
        return addReviews(p, type, shoppers, rnd, now, daysAgo, 3 + (int) (popularity * popularity * 36)); // most have a few
    }

    private int addReviews(Product p, DemoCatalog.Type type, List<User> shoppers, Random rnd, Instant now, long daysAgo,
                           int count) {
        if (count <= 0) return 0;
        double quality = 3.4 + rnd.nextDouble() * 1.4;            // how good this product really is
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

    // ---------- the larger catalogue ----------

    /**
     * Adds {@code count} more products spread over the demo categories, sold by Pacific, the category's store and a
     * dozen more stores; with reviews (a few have none yet), some "was" prices, other stores' offers, coupons and
     * colours and sizes. Each category is its own transaction. Returns {products, reviews, offers, variations, stores}.
     */
    private int[] seedMoreProducts(int count) {
        Random rnd = new Random(20261001L);
        Instant now = Instant.now();
        String unusable = encoder.encode(UUID.randomUUID().toString());
        int[] totals = new int[5];

        // The stores: the first demo ones, plus more (added now if missing).
        Map<String, SellerProfile> byName = new HashMap<>();
        for (DemoCatalog.Store st : DemoCatalog.STORES) sellers.findBySlug(slugify(st.name())).ifPresent(sp -> byName.put(st.name(), sp));
        if (byName.isEmpty()) return totals; // not a demo shop
        for (DemoCatalog.ExtraStore st : DemoCatalog.EXTRA_STORES) {
            SellerProfile existing = sellers.findBySlug(slugify(st.name())).orElse(null);
            if (existing == null) {
                existing = tx.execute(status -> {
                    User owner = users.save(confirmed(new User(st.name(), "seller." + slugify(st.name()) + "@example.com", null,
                            unusable, Role.CUSTOMER)));
                    SellerProfile profile = new SellerProfile(owner, st.name(), slugify(st.name()), st.description());
                    profile.setStatus(SellerStatus.APPROVED, null);
                    profile.setRating(BigDecimal.valueOf(3.9 + rnd.nextInt(10) / 10.0).setScale(2), 10 + rnd.nextInt(400));
                    return sellers.save(profile);
                });
                totals[4]++;
            }
            byName.put(st.name(), existing);
        }
        List<User> shoppers = users.findAll().stream().filter(u -> u.getEmail() != null && u.getEmail().startsWith("demo.")).toList();
        if (shoppers.isEmpty()) return totals;
        Set<String> names = products.findAll().stream().map(p -> p.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(java.util.HashSet::new));

        List<DemoCatalog.Category> defs = DemoCatalog.CATEGORIES;
        for (int c = 0; c < defs.size(); c++) {
            DemoCatalog.Category def = defs.get(c);
            int share = count / defs.size() + (c < count % defs.size() ? 1 : 0);
            List<SellerProfile> extra = DemoCatalog.EXTRA_STORES.stream()
                    .filter(st -> st.categories().isEmpty() || st.categories().contains(def.name()))
                    .map(st -> byName.get(st.name())).toList();
            int[] made = tx.execute(status -> {
                Category category = categories.findAll().stream().filter(x -> x.getName().equalsIgnoreCase(def.name())).findFirst()
                        .orElseGet(() -> categories.save(new Category(def.name(), slugify(def.name()))));
                List<Product> added = new ArrayList<>();
                int reviewCount = 0;
                for (int i = 0; added.size() < share && i < share * 20; i++) {
                    DemoCatalog.Type type = def.types().get(rnd.nextInt(def.types().size()));
                    String brand = def.brands().get(rnd.nextInt(def.brands().size()));
                    boolean isBook = "Books".equals(def.name());
                    String name = isBook ? bookTitle(type, rnd) : productName(def, brand, type, rnd);
                    if (!names.add(name.toLowerCase(Locale.ROOT))) continue;

                    BigDecimal price = price(type, rnd);
                    BigDecimal listPrice = rnd.nextInt(100) < 22 ? listPriceFor(price, rnd) : null;
                    Product p = new Product(name, description(brand, type, isBook), price, stock(rnd),
                            "demo:" + type.art() + ":" + hue(name), category);
                    p.update(p.getName(), p.getDescription(), price, listPrice, p.getStock(), p.getImageUrl(), category, true);
                    int roll = rnd.nextInt(100);
                    p.setSeller(roll < 18 ? null : roll < 50 ? byName.get(def.store()) : extra.get(rnd.nextInt(extra.size())));
                    long daysAgo = 1 + rnd.nextInt(365);
                    p.setCreatedAt(now.minus(Duration.ofDays(daysAgo)));
                    p = products.save(p);
                    double popularity = rnd.nextDouble();
                    int reviewsWanted = rnd.nextInt(100) < 8 ? 0 : 1 + (int) (popularity * popularity * popularity * 38);
                    reviewCount += addReviews(p, type, shoppers, rnd, now, daysAgo, reviewsWanted);
                    added.add(p);
                }
                List<SellerProfile> nearby = new ArrayList<>(extra);
                nearby.add(byName.get(def.store()));
                int offers = addOffers(added, nearby, rnd, 9);
                int variations = addVariations(added, rnd, 6, 4);
                for (int i = 7; i < added.size(); i += 40) {
                    Product p = added.get(i);
                    promotions.createCoupon(p.getSeller() == null ? null : p.getSeller().getId(), p.getId(), 5 + rnd.nextInt(4) * 5, 200, 90);
                }
                return new int[] {added.size(), reviewCount, offers, variations};
            });
            for (int k = 0; k < 4; k++) totals[k] += made[k];
        }
        return totals;
    }

    /** "Resonic Pulse 482 Plus Wireless Over-Ear Headphones". */
    private static String productName(DemoCatalog.Category def, String brand, DemoCatalog.Type type, Random rnd) {
        String series = def.series().get(rnd.nextInt(def.series().size()));
        String edition = DemoCatalog.EDITIONS.get(rnd.nextInt(DemoCatalog.EDITIONS.size()));
        return (brand + " " + series + " " + (100 + rnd.nextInt(900)) + " " + edition).replaceAll("\\s+", " ").trim()
                + " " + type.label();
    }

    /** "The Quiet Harbour (Paperback)": an invented title in the format of one of the category's books. */
    private static String bookTitle(DemoCatalog.Type type, Random rnd) {
        String adjective = DemoCatalog.TITLE_ADJECTIVES.get(rnd.nextInt(DemoCatalog.TITLE_ADJECTIVES.size()));
        String noun = DemoCatalog.TITLE_NOUNS.get(rnd.nextInt(DemoCatalog.TITLE_NOUNS.size()));
        String other = DemoCatalog.TITLE_NOUNS.get(rnd.nextInt(DemoCatalog.TITLE_NOUNS.size()));
        String title = switch (rnd.nextInt(4)) {
            case 0 -> "The " + adjective + " " + noun;
            case 1 -> "The " + noun + " of the " + adjective + " " + other;
            case 2 -> "A " + adjective + " " + noun;
            default -> noun + " and " + other;
        };
        java.util.regex.Matcher format = java.util.regex.Pattern.compile("\\(([^)]*)\\)$").matcher(type.label());
        return format.find() ? title + " (" + format.group(1) + ")" : title;
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
