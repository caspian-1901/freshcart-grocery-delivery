package com.isha.grocery.service;

import com.isha.grocery.domain.Address;
import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.repo.AddressRepository;
import com.isha.grocery.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    private final AddressRepository addresses;
    private final CurrentUser currentUser;

    public AddressService(AddressRepository addresses, CurrentUser currentUser) {
        this.addresses = addresses;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Responses.AddressView> list() {
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(currentUser.id())
                .stream().map(AddressService::toView).toList();
    }

    @Transactional
    public Responses.AddressView create(Requests.AddressPayload payload) {
        Long userId = currentUser.id();
        List<Address> existing = addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId);
        boolean makeDefault = payload.defaultAddress() || existing.isEmpty();

        if (makeDefault) {
            existing.forEach(a -> a.setDefaultAddress(false));
            addresses.saveAll(existing);
        }

        Address address = addresses.save(Address.builder()
                .user(currentUser.require())
                .label(payload.label().trim())
                .line1(payload.line1().trim())
                .line2(payload.line2())
                .city(payload.city().trim())
                .pincode(payload.pincode().trim())
                .phone(payload.phone().trim())
                .defaultAddress(makeDefault)
                .build());

        return toView(address);
    }

    @Transactional
    public void delete(Long id) {
        Address address = addresses.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Address not found."));
        addresses.delete(address);
    }

    @Transactional(readOnly = true)
    public Address requireOwned(Long id) {
        return addresses.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> ApiException.notFound("Please choose a valid delivery address."));
    }

    static Responses.AddressView toView(Address a) {
        return new Responses.AddressView(a.getId(), a.getLabel(), a.getLine1(), a.getLine2(),
                a.getCity(), a.getPincode(), a.getPhone(), a.isDefaultAddress());
    }
}
