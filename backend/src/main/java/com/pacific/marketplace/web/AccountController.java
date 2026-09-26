package com.pacific.marketplace.web;

import com.pacific.marketplace.service.AccountService;
import com.pacific.marketplace.service.AddressService;
import com.pacific.marketplace.web.dto.AddressDtos.AddressDto;
import com.pacific.marketplace.web.dto.AddressDtos.AddressRequest;
import com.pacific.marketplace.web.dto.AuthDtos.AuthResponse;
import com.pacific.marketplace.web.dto.AuthDtos.ChangePasswordRequest;
import com.pacific.marketplace.web.dto.AuthDtos.UpdateProfileRequest;
import com.pacific.marketplace.web.dto.AuthDtos.UserDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A customer's own account: name, password and saved addresses. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService account;
    private final AddressService addresses;

    public AccountController(AccountService account, AddressService addresses) {
        this.account = account;
        this.addresses = addresses;
    }

    @PatchMapping
    public UserDto rename(@Valid @RequestBody UpdateProfileRequest req, @AuthenticationPrincipal Jwt jwt) {
        return account.rename(CurrentUser.id(jwt), req.name());
    }

    /** Returns a fresh sign-in token: every other session is signed out, this one carries on. */
    @PostMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest req, @AuthenticationPrincipal Jwt jwt) {
        return account.changePassword(CurrentUser.id(jwt), req.currentPassword(), req.newPassword());
    }

    /** "Sign out of all other devices": this one gets a new token back. */
    @PostMapping("/sessions/sign-out-others")
    public AuthResponse signOutOthers(@AuthenticationPrincipal Jwt jwt) {
        return account.signOutEverywhereElse(CurrentUser.id(jwt));
    }

    @GetMapping("/addresses")
    public List<AddressDto> addresses(@AuthenticationPrincipal Jwt jwt) {
        return addresses.list(CurrentUser.id(jwt));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressDto addAddress(@Valid @RequestBody AddressRequest req, @AuthenticationPrincipal Jwt jwt) {
        return addresses.create(CurrentUser.id(jwt), req);
    }

    @PutMapping("/addresses/{id}")
    public AddressDto updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        return addresses.update(CurrentUser.id(jwt), id, req);
    }

    @PostMapping("/addresses/{id}/default")
    public AddressDto makeDefault(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return addresses.makeDefault(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        addresses.delete(CurrentUser.id(jwt), id);
    }
}
