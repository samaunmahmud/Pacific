package com.pacific.marketplace;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Builds a small SQLite file with the OLD JavaFX schema and imports it into a fresh database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.legacy-import.sqlite-path=${java.io.tmpdir}/pacific-legacy-test.db"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyImportTest {

    static {
        try {
            createLegacyDatabase(Path.of(System.getProperty("java.io.tmpdir"), "pacific-legacy-test.db"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired ReviewRepository reviews;

    @Test
    void importsProductsReviewsAndHashesPlaintextPasswords() throws Exception {
        assertThat(products.count()).isEqualTo(2);
        Product headphones = products.findAll().stream().filter(p -> p.getName().equals("Wireless Headphones"))
                .findFirst().orElseThrow();
        assertThat(headphones.getStock()).isEqualTo(50);

        assertThat(reviews.count()).isEqualTo(2);
        Review split = reviews.findAll().stream().filter(r -> r.getTitle() != null).findFirst().orElseThrow();
        assertThat(split.getTitle()).isEqualTo("Great sound");
        assertThat(split.getComment()).isEqualTo("Very clear bass");
        assertThat(reviews.countByStatus(ReviewStatus.FLAGGED)).isEqualTo(1);
        assertThat(headphones.getRatingCount()).isEqualTo(1); // the flagged review is excluded from the rating

        // the old plaintext passwords still work — now stored as BCrypt hashes
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice@gmail.com\",\"password\":\"aj4u2ugiu4\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"admin1\",\"password\":\"adminpass1\"}"))
                .andExpect(status().isOk());
    }

    private static void createLegacyDatabase(Path file) throws Exception {
        Files.deleteIfExists(file);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE customer (customer_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at TEXT, updated_at TEXT)");
            s.execute("CREATE TABLE product (product_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "description TEXT, price REAL, created_at TEXT, updated_at TEXT)");
            s.execute("CREATE TABLE admin (admin_id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL, created_at TEXT, updated_at TEXT)");
            s.execute("CREATE TABLE review (review_id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, "
                    + "customer_id INTEGER NOT NULL, rating INTEGER NOT NULL, comment TEXT NOT NULL, image_url TEXT, "
                    + "helpful_count INTEGER DEFAULT 0, review_status TEXT NOT NULL DEFAULT 'visible', created_at TEXT, "
                    + "updated_at TEXT, updated_by_admin INTEGER DEFAULT 0, unhelpful_count INTEGER DEFAULT 0)");
            s.execute("INSERT INTO customer (name,email,password_hash,created_at) VALUES "
                    + "('Alice Johnson','Alice@gmail.com','aj4u2ugiu4','2026-03-15 23:15:18'),"
                    + "('Bob Smith','bob@email.com','bobpassword','2026-03-15 23:15:18')");
            s.execute("INSERT INTO product (name,description,price,created_at) VALUES "
                    + "('Wireless Headphones','Noise-cancelling',89.99,'2026-03-15 23:15:18'),"
                    + "('Gaming Mouse','RGB',25.99,'2026-03-15 23:16:24')");
            s.execute("INSERT INTO admin (username,password_hash) VALUES ('admin1','adminpass1')");
            s.execute("INSERT INTO review (product_id,customer_id,rating,comment,image_url,helpful_count,review_status,"
                    + "created_at,unhelpful_count) VALUES "
                    + "(1,1,5,'Great sound: Very clear bass','C:\\\\pics\\\\a.png',3,'visible','2026-03-16 10:00:00',1),"
                    + "(1,2,1,'Rude words',NULL,0,'flagged','2026-03-16 11:00:00',0)");
        }
    }
}
