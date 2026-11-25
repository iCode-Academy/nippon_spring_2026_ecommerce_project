package mn.icode.controller;

import mn.icode.service.AddressService;
import mn.icode.dto.AddressForm;
import mn.icode.dto.AddressResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressApiController {

    private final AddressService addressService;

    public AddressApiController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses() {
        List<AddressResponse> addresses = addressService.listAddresses();
        return ResponseEntity.ok(addresses);
    }

    @GetMapping("/default")
    public ResponseEntity<AddressResponse> getDefaultAddress() {
        AddressResponse defaultAddr = addressService.getDefaultAddress();
        if (defaultAddr == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(defaultAddr);
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody AddressForm form) {
        AddressResponse created = addressService.createAddress(form);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressForm form) {
        AddressResponse updated = addressService.updateAddress(id, form);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }
}