package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.demo.DemoDataSeeder;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The opt-in demo data. Uses its own in-memory database so it can't leak into the other tests' shops. */
@TestPropertySource(properties = {
        "app.demo-data.enabled=true",
        "app.demo-data.extra-products=700", // the real default is 2000; fewer keeps the test quick
        "spring.datasource.url=jdbc:h2:mem:demoseed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"})
class DemoDataSeederTest extends IntegrationTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired ProductRepository productRepository;
    @Autowired ReviewRepository reviewRepository;

    private JsonNode search(String query) throws Exception {
        return read(mvc.perform(get("/api/products?size=48&" + query)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void addsARealisticShop() throws Exception {
        // catalog pages (one per product); other stores' offers on them are extra listings
        // (a product's other colours and sizes are pages of their own, added later without reviews: leave them out)
        var all = productRepository.findAll();
        var pages = all.stream().filter(p -> !p.isOffer() && (p.getFamilyId() == null || all.stream()
                .noneMatch(o -> p.getFamilyId().equals(o.getFamilyId()) && o.getId() < p.getId()))).toList();
        // ~170 first products plus 700 more (the larger catalogue), all with different names
        assertThat(pages).hasSizeBetween(840, 900);
        assertThat(pages.stream().map(p -> p.getName().toLowerCase()).distinct().count()).isEqualTo(pages.size());
        assertThat(pages).anyMatch(p -> p.getName().matches("The .+ \\((Paperback|Hardcover)\\)"));
        assertThat(reviewRepository.count()).isGreaterThan(1000L);
        JsonNode categories = read(mvc.perform(get("/api/categories")).andReturn());
        assertThat(categories.size()).isGreaterThanOrEqualTo(13);
        assertThat(search("").get("totalItems").asInt()).isGreaterThan(840);

        // ratings on each product agree with the reviews behind them, and the spread looks like a real shop
        long withReviews = 0;
        double lowest = 5, highest = 0;
        for (var p : pages) {
            long visible = reviewRepository.visibleStats(p.getId()).count();
            assertThat(p.getRatingCount()).isEqualTo((int) visible);
            if (visible > 0) {
                withReviews++;
                lowest = Math.min(lowest, p.getRatingAvg().doubleValue());
                highest = Math.max(highest, p.getRatingAvg().doubleValue());
            }
        }
        assertThat(withReviews).isBetween((long) (pages.size() * 0.85), pages.size() - 1L); // a few new ones have none yet
        assertThat(lowest).isLessThan(4.0);
        assertThat(highest).isGreaterThan(4.4);

        // some deals, some low stock, some sold by Pacific, most by stores
        assertThat(search("deals=true").get("totalItems").asInt()).isBetween(100, 400);
        assertThat(pages.stream().filter(p -> p.getSeller() == null).count()).isBetween(100L, 230L);
        assertThat(pages.stream().filter(p -> p.getSeller() != null).count()).isGreaterThan(600L);
        // twenty stores, the newer ones selling in their own categories
        var stores = pages.stream().filter(p -> p.getSeller() != null).map(p -> p.getSeller().getStoreName()).distinct().toList();
        assertThat(stores).hasSize(20).contains("Kettle & Crumb", "Everyday Essentials Co.");
        assertThat(pages).filteredOn(p -> p.getSeller() != null && p.getSeller().getStoreName().equals("Chapter & Verse"))
                .allMatch(p -> p.getCategory().getName().equals("Books"));

        // about one product in five is also sold by other stores, some of them used
        var offers = productRepository.findAll().stream().filter(p -> p.isOffer()).toList();
        assertThat(offers).hasSizeBetween(100, 250);
        assertThat(offers).anyMatch(o -> o.getCondition() != com.pacific.marketplace.domain.ItemCondition.NEW);
        assertThat(offers).allMatch(o -> !o.getSeller().getId().equals(
                productRepository.findById(o.getGroupId()).orElseThrow().getSeller() == null ? -1L
                        : productRepository.findById(o.getGroupId()).orElseThrow().getSeller().getId()));
        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getStock() > 0 && p.getStock() <= 5)).isTrue();

        // some clothes and gadgets come in other colours (clothes in sizes too), each family one card in search
        var variations = all.stream().filter(p -> p.getFamilyId() != null).toList();
        assertThat(variations).hasSizeBetween(100, 300);
        assertThat(variations).anyMatch(p -> p.getOption2() != null);
        JsonNode fashion = search("category=fashion");
        boolean grouped = false;
        for (JsonNode card : fashion.get("items")) grouped |= card.get("variationCount").asInt() >= 9;
        assertThat(grouped).isTrue();
    }

    @Test
    void filtersAndSortsWorkOnRealisticData() throws Exception {
        JsonNode cheap = search("maxPrice=20");
        assertThat(cheap.get("items")).isNotEmpty();
        cheap.get("items").forEach(p -> assertThat(p.get("boxPrice").decimalValue()).isLessThanOrEqualTo(new java.math.BigDecimal("20")));

        JsonNode mid = search("minPrice=50&maxPrice=100");
        assertThat(mid.get("items")).isNotEmpty();
        mid.get("items").forEach(p -> assertThat(p.get("boxPrice").decimalValue()).isBetween(new java.math.BigDecimal("50"), new java.math.BigDecimal("100")));

        JsonNode good = search("minRating=4.5");
        assertThat(good.get("items")).isNotEmpty();
        good.get("items").forEach(p -> assertThat(p.get("ratingAvg").asDouble()).isGreaterThanOrEqualTo(4.5));
        assertThat(good.get("totalItems").asInt()).isLessThan(search("").get("totalItems").asInt());

        JsonNode popular = search("sort=popular");
        int previous = Integer.MAX_VALUE;
        for (JsonNode p : popular.get("items")) {
            assertThat(p.get("ratingCount").asInt()).isLessThanOrEqualTo(previous);
            previous = p.get("ratingCount").asInt();
        }
    }

    @Test
    void theDemoShopperCanSignInAndSeedingIsOnlyDoneOnce() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("identifier", DemoDataSeeder.SHOPPER_EMAIL, "password", DemoDataSeeder.SHOPPER_PASSWORD))))
                .andExpect(status().isOk());

        long products = productRepository.count();
        long reviews = reviewRepository.count();
        seeder.run(null); // a restart must not add it all again
        assertThat(productRepository.count()).isEqualTo(products);
        assertThat(reviewRepository.count()).isEqualTo(reviews);
    }

    /** Every drawn image the seeder asks for has to exist in the storefront, or shoppers see blank tiles. */
    @Test
    void everyImageKindIsDrawnByTheStorefront() throws Exception {
        Path kinds = Path.of("../frontend/src/components/demoArtKinds.ts");
        assumeTrue(Files.exists(kinds), "frontend not checked out next to the backend");
        Set<String> drawn = new HashSet<>();
        Matcher m = Pattern.compile("'([a-z]+)'").matcher(Files.readString(kinds));
        while (m.find()) drawn.add(m.group(1));

        Set<String> used = new HashSet<>();
        productRepository.findAll().forEach(p -> {
            assertThat(p.getImageUrl()).startsWith("demo:");
            used.add(p.getImageUrl().split(":")[1]);
        });
        assertThat(drawn).containsAll(used);
    }
}
