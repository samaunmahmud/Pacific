package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.UserAddress;
import com.pacific.marketplace.repo.UserAddressRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.AddressDtos.AddressDto;
import com.pacific.marketplace.web.dto.AddressDtos.AddressRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's saved delivery addresses. Exactly one is the default (used first at checkout) whenever any exist. Changes
 * lock the customer's row, so two devices editing at once can't leave two defaults.
 */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final UserAddressRepository addresses;
    private final UserRepository users;

    public AddressService(UserAddressRepository addresses, UserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AddressDto> list(Long userId) {
        return addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream().map(AddressDto::from).toList();
    }

    @Transactional
    public AddressDto create(Long userId, AddressRequest req) {
        var user = users.lockById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES) {
            throw ApiException.conflict("You can save up to " + MAX_ADDRESSES + " addresses. Please delete one first.");
        }
        boolean first = addresses.countByUserId(userId) == 0;
        UserAddress a = new UserAddress(user, req.name().strip(), req.line1().strip(), Text.clean(req.line2()),
                req.city().strip(), req.postcode().strip(), req.country().strip());
        if (first || Boolean.TRUE.equals(req.makeDefault())) {
            clearDefault(userId);
            a.setDefault(true);
        }
        return AddressDto.from(addresses.saveAndFlush(a));
    }

    @Transactional
    public AddressDto update(Long userId, Long id, AddressRequest req) {
        users.lockById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        UserAddress a = own(userId, id);
        a.set(req.name().strip(), req.line1().strip(), Text.clean(req.line2()), req.city().strip(),
                req.postcode().strip(), req.country().strip());
        if (Boolean.TRUE.equals(req.makeDefault()) && !a.isDefault()) {
            clearDefault(userId);
            a.setDefault(true);
        }
        return AddressDto.from(addresses.saveAndFlush(a));
    }

    @Transactional
    public AddressDto makeDefault(Long userId, Long id) {
        users.lockById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        UserAddress a = own(userId, id);
        clearDefault(userId);
        a.setDefault(true);
        return AddressDto.from(addresses.saveAndFlush(a));
    }

    /** Deleting the default promotes the most recently saved of the rest. */
    @Transactional
    public void delete(Long userId, Long id) {
        users.lockById(userId).orElseThrow(() -> ApiException.unauthorized("Please sign in."));
        UserAddress a = own(userId, id);
        boolean wasDefault = a.isDefault();
        addresses.delete(a);
        addresses.flush();
        if (wasDefault) {
            addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream().findFirst().ifPresent(next -> next.setDefault(true));
        }
    }

    private UserAddress own(Long userId, Long id) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Address not found."));
    }

    private void clearDefault(Long userId) {
        addresses.findByUserIdOrderByIsDefaultDescIdDesc(userId).stream().filter(UserAddress::isDefault)
                .forEach(x -> x.setDefault(false));
        addresses.flush();
    }
}
