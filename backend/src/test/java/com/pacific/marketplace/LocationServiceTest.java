package com.pacific.marketplace;

import com.pacific.marketplace.service.LocationService;
import com.pacific.marketplace.web.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** "Deliver to" lookups, against a stand-in for postcodes.io. */
class LocationServiceTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().toString();
            requests.add(path);
            String body;
            int status = 200;
            if (path.equals("/postcodes/UB8%203PH")) {
                body = "{\"status\":200,\"result\":{\"postcode\":\"UB8 3PH\",\"outcode\":\"UB8\",\"admin_district\":\"Hillingdon\",\"country\":\"England\"}}";
            } else if (path.startsWith("/postcodes?lat=51.507400&lon=-0.127800")) {
                body = "{\"status\":200,\"result\":[{\"postcode\":\"WC2N 5DU\",\"outcode\":\"WC2N\",\"admin_district\":\"Westminster\",\"country\":\"England\"}]}";
            } else if (path.startsWith("/postcodes?lat=")) {
                body = "{\"status\":200,\"result\":null}";
            } else if (path.startsWith("/postcodes/BROKEN")) {
                body = "oops";
                status = 500;
            } else {
                body = "{\"status\":404,\"error\":\"Postcode not found\"}";
                status = 404;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aPostcodeIsTidiedLookedUpOnceAndNamed() {
        LocationService s = new LocationService(base, 100);
        LocationService.Location l = s.postcode(" ub83ph ", "ip");
        assertThat(l).isEqualTo(new LocationService.Location("UB8 3PH", "UB8", "Hillingdon", "England"));
        s.postcode("UB8 3PH", "ip");
        assertThat(requests).containsExactly("/postcodes/UB8%203PH"); // the second came from the cache
    }

    @Test
    void badOrUnknownPostcodesAreRefusedClearly() {
        LocationService s = new LocationService(base, 100);
        assertThatThrownBy(() -> s.postcode("hello", "ip")).isInstanceOf(ApiException.class)
                .hasMessageContaining("Enter a UK postcode");
        assertThat(requests).isEmpty(); // not even sent
        assertThatThrownBy(() -> s.postcode("ZZ99 1ZZ", "ip")).isInstanceOf(ApiException.class)
                .hasMessageContaining("couldn't find ZZ99 1ZZ");
    }

    @Test
    void theNearestPostcodeToADevice() {
        LocationService s = new LocationService(base, 100);
        assertThat(s.near(51.5074, -0.1278, "ip").place()).isEqualTo("Westminster");
        assertThatThrownBy(() -> s.near(55.0, -3.0, "ip")).hasMessageContaining("couldn't find a UK postcode near you");
        assertThatThrownBy(() -> s.near(40.7, -74.0, "ip")).hasMessageContaining("only deliver in the UK");
    }

    @Test
    void whenTheServiceIsDownAPostcodeStillWorksWithoutItsPlace() {
        LocationService down = new LocationService("http://127.0.0.1:1", 100);
        assertThat(down.postcode("SW1A 1AA", "ip")).isEqualTo(new LocationService.Location("SW1A 1AA", "SW1A", null, null));
        assertThatThrownBy(() -> down.near(51.5, -0.1, "ip")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void lookupsAreLimitedPerVisitor() {
        LocationService s = new LocationService(base, 2);
        s.near(51.5074, -0.1278, "a");
        s.near(51.5074, -0.1278, "a");
        assertThatThrownBy(() -> s.near(51.5074, -0.1278, "a")).hasMessageContaining("Too many location lookups");
        assertThat(s.near(51.5074, -0.1278, "b").postcode()).isEqualTo("WC2N 5DU");
    }
}
