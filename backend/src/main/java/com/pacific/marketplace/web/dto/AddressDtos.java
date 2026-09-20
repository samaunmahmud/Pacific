package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.UserAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AddressDtos {

    private AddressDtos() {
    }

    /** makeDefault: use this address first at checkout (the first address saved is the default anyway). */
    public record AddressRequest(
            @NotBlank(message = "Please enter the recipient's name.") @Size(max = 120) String name,
            @NotBlank(message = "Please enter the first line of the address.") @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank(message = "Please enter the town or city.") @Size(max = 80) String city,
            @NotBlank(message = "Please enter the postcode.") @Size(max = 20) String postcode,
            @NotBlank(message = "Please enter the country.") @Size(max = 80) String country,
            Boolean makeDefault) {
    }

    public record AddressDto(Long id, String name, String line1, String line2, String city, String postcode,
                             String country, boolean isDefault) {
        public static AddressDto from(UserAddress a) {
            return new AddressDto(a.getId(), a.getName(), a.getLine1(), a.getLine2(), a.getCity(), a.getPostcode(),
                    a.getCountry(), a.isDefault());
        }
    }
}
