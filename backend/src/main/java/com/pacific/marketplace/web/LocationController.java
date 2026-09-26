package com.pacific.marketplace.web;

import com.pacific.marketplace.service.LocationService;
import com.pacific.marketplace.service.LocationService.Location;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Deliver to": the place a postcode is in, or the postcode nearest the shopper's device. Open to everyone. */
@RestController
@RequestMapping("/api/location")
public class LocationController {

    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping("/postcode/{postcode}")
    public Location postcode(@PathVariable String postcode, HttpServletRequest http) {
        return locations.postcode(postcode, http.getRemoteAddr());
    }

    @GetMapping("/near")
    public Location near(@RequestParam double lat, @RequestParam double lon, HttpServletRequest http) {
        return locations.near(lat, lon, http.getRemoteAddr());
    }
}
