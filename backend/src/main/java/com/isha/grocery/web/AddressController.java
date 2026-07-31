package com.isha.grocery.web;

import com.isha.grocery.dto.Requests;
import com.isha.grocery.dto.Responses;
import com.isha.grocery.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public List<Responses.AddressView> list() {
        return addresses.list();
    }

    @PostMapping
    public Responses.AddressView create(@Valid @RequestBody Requests.AddressPayload payload) {
        return addresses.create(payload);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        addresses.delete(id);
    }
}
