package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A product can have up to eight photos: the main one (imageUrl) and up to seven more, shown as a gallery. */
class ProductGalleryTest extends IntegrationTest {

    private static final String A = "https://img.example.com/a.jpg";
    private static final String B = "https://img.example.com/b.jpg";
    private static final String C = "/api/images/" + "c".repeat(32) + ".jpg";

    private static Map<String, Object> product(String imageUrl, List<String> moreImages) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", "Gallery lamp");
        p.put("price", "20.00");
        p.put("stock", 4);
        p.put("imageUrl", imageUrl);
        if (moreImages != null) p.put("moreImages", moreImages);
        return p;
    }

    private JsonNode save(String method, String url, Map<String, Object> body) throws Exception {
        var req = "PUT".equals(method) ? put(url) : post(url);
        return read(mvc.perform(bearer(req, adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().is2xxSuccessful()).andReturn());
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void shoppersSeeEveryPhotoInOrder() throws Exception {
        long id = save("POST", "/api/admin/products", product(A, List.of(B, C))).get("id").asLong();

        JsonNode shown = read(mvc.perform(get("/api/products/" + id)).andExpect(status().isOk()).andReturn());
        assertThat(shown.get("imageUrl").asText()).isEqualTo(A);
        assertThat(texts(shown.get("moreImages"))).containsExactly(B, C);
    }

    @Test
    void savingWithoutTheListKeepsThePhotosAndAnEmptyListRemovesThem() throws Exception {
        long id = save("POST", "/api/admin/products", product(A, List.of(B, C))).get("id").asLong();

        // Older clients (and the QA scripts) don't send moreImages: the gallery must survive their edits.
        JsonNode kept = save("PUT", "/api/admin/products/" + id, product(A, null));
        assertThat(texts(kept.get("moreImages"))).containsExactly(B, C);

        JsonNode reordered = save("PUT", "/api/admin/products/" + id, product(C, List.of(A)));
        assertThat(reordered.get("imageUrl").asText()).isEqualTo(C);
        assertThat(texts(reordered.get("moreImages"))).containsExactly(A);

        JsonNode cleared = save("PUT", "/api/admin/products/" + id, product(C, List.of()));
        assertThat(texts(cleared.get("moreImages"))).isEmpty();
    }

    @Test
    void repeatsAreDroppedAndWithoutAMainPhotoTheFirstExtraTakesItsPlace() throws Exception {
        JsonNode p = save("POST", "/api/admin/products", product(A, List.of(B, A, B)));
        assertThat(p.get("imageUrl").asText()).isEqualTo(A);
        assertThat(texts(p.get("moreImages"))).containsExactly(B);

        JsonNode promoted = save("POST", "/api/admin/products", product("", List.of(B, C)));
        assertThat(promoted.get("imageUrl").asText()).isEqualTo(B);
        assertThat(texts(promoted.get("moreImages"))).containsExactly(C);
    }

    @Test
    void atMostEightPhotosAndEachMustBeAPhotoAddress() throws Exception {
        List<String> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) eight.add("https://img.example.com/" + i + ".jpg");
        mvc.perform(bearer(post("/api/admin/products"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(product(A, eight)))).andExpect(status().isBadRequest());

        for (String bad : new String[]{"javascript:alert(1)", "/api/images/../../secret.jpg", "x".repeat(501)}) {
            mvc.perform(bearer(post("/api/admin/products"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(product(A, List.of(bad))))).andExpect(status().isBadRequest());
        }

        JsonNode full = save("POST", "/api/admin/products", product(A, eight.subList(0, 7)));
        assertThat(full.get("moreImages")).hasSize(7);
    }
}
