package com.pacific.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ShippingAddress {

    @Column(name = "ship_name", nullable = false, length = 120)
    private String name;

    @Column(name = "ship_line1", nullable = false, length = 160)
    private String line1;

    @Column(name = "ship_line2", length = 160)
    private String line2;

    @Column(name = "ship_city", nullable = false, length = 80)
    private String city;

    @Column(name = "ship_postcode", nullable = false, length = 20)
    private String postcode;

    @Column(name = "ship_country", nullable = false, length = 80)
    private String country;

    protected ShippingAddress() {
    }

    public ShippingAddress(String name, String line1, String line2, String city, String postcode, String country) {
        this.name = name;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.postcode = postcode;
        this.country = country;
    }

    public String getName() { return name; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getPostcode() { return postcode; }
    public String getCountry() { return country; }
}
