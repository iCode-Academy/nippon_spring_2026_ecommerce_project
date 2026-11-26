package mn.icode.service;

import mn.icode.dto.AddressForm;
import mn.icode.dto.AddressResponse;
import mn.icode.entity.Address;
import mn.icode.entity.User;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.repository.AddressRepository;
import mn.icode.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    /** Get the currently authenticated user. */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /** List all addresses for the current user. */
    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses() {
        User user = getCurrentUser();
        return addressRepository.findByUserId(user.getId()).stream()
                .map(addr -> new AddressResponse(
                        addr.getId(),
                        addr.getTitle(),
                        addr.getCity(),
                        addr.getDistrict(),
                        addr.getAddressLine(),
                        addr.getPhone(),
                        addr.getDefaultAddress()))
                .collect(Collectors.toList());
    }

    /** Get the default address for the current user, or null if none. */
    @Transactional(readOnly = true)
    public AddressResponse getDefaultAddress() {
        User user = getCurrentUser();
        Optional<Address> defaultAddr = addressRepository.findByUserIdAndDefaultAddressTrue(user.getId());
        if (defaultAddr.isEmpty()) {
            return null;
        }
        Address addr = defaultAddr.get();
        return new AddressResponse(
                addr.getId(),
                addr.getTitle(),
                addr.getCity(),
                addr.getDistrict(),
                addr.getAddressLine(),
                addr.getPhone(),
                addr.getDefaultAddress());
    }

    /** Create a new address for the current user. */
    @Transactional
    public AddressResponse createAddress(AddressForm form) {
        User user = getCurrentUser();

        Address address = new Address();
        address.setUser(user);
        address.setTitle(form.getTitle());
        address.setCity(form.getCity());
        address.setDistrict(form.getDistrict());
        address.setAddressLine(form.getAddressLine());
        address.setPhone(form.getPhone());
        address.setDefaultAddress(form.getDefaultAddress() != null && form.getDefaultAddress());

        // If this is set as default, clear any existing default address
        if (address.getDefaultAddress()) {
            addressRepository.findByUserIdAndDefaultAddressTrue(user.getId())
                    .ifPresent(existing -> existing.setDefaultAddress(false));
        }

        addressRepository.save(address);
        return new AddressResponse(
                address.getId(),
                address.getTitle(),
                address.getCity(),
                address.getDistrict(),
                address.getAddressLine(),
                address.getPhone(),
                address.getDefaultAddress());
    }

    /** Update an existing address. Enforces ownership. */
    @Transactional
    public AddressResponse updateAddress(Long id, AddressForm form) {
        User user = getCurrentUser();

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        // Ownership check - user can only update their own addresses
        if (!address.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to update this address");
        }

        address.setTitle(form.getTitle());
        address.setCity(form.getCity());
        address.setDistrict(form.getDistrict());
        address.setAddressLine(form.getAddressLine());
        address.setPhone(form.getPhone());

        // Handle default address change: if marking as default, clear any existing default
        Boolean newDefault = form.getDefaultAddress();
        if (newDefault != null && newDefault) {
            addressRepository.findByUserIdAndDefaultAddressTrue(user.getId())
                    .filter(existing -> !existing.getId().equals(address.getId()))
                    .ifPresent(existing -> existing.setDefaultAddress(false));
        }

        address.setDefaultAddress(newDefault != null ? newDefault : address.getDefaultAddress());
        addressRepository.save(address);
        return new AddressResponse(
                address.getId(),
                address.getTitle(),
                address.getCity(),
                address.getDistrict(),
                address.getAddressLine(),
                address.getPhone(),
                address.getDefaultAddress());
    }

    /** Delete an address. Enforces ownership and handles default address clearing. */
    @Transactional
    public void deleteAddress(Long id) {
        User user = getCurrentUser();

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));

        // Ownership check
        if (!address.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this address");
        }

        // If deleting the default address, clear the default flag before deletion
        if (address.getDefaultAddress()) {
            address.setDefaultAddress(false);
            addressRepository.save(address);
        }

        addressRepository.deleteById(id);
    }
}