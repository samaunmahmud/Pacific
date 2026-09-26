package com.pacific.marketplace;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "Deliver to" endpoints are open to signed-out shoppers and check their input before looking anything up. */
class LocationApiTest extends IntegrationTest {

    @Test
    void anyoneCanAskAndNonsenseIsRefusedWithoutALookup() throws Exception {
        mvc.perform(get("/api/location/postcode/not-a-postcode")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a UK postcode, like UB8 1AA."));
        mvc.perform(get("/api/location/near").param("lat", "40.7").param("lon", "-74.0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/location/near")).andExpect(status().isBadRequest());
    }
}
