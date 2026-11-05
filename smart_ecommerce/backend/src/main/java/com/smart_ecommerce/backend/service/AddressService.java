package com.smart_ecommerce.backend.service;

import com.smart_ecommerce.backend.dto.address.AddressRequest;
import com.smart_ecommerce.backend.dto.address.AddressResponse;
import com.smart_ecommerce.backend.entity.Address;
import com.smart_ecommerce.backend.entity.User;
import com.smart_ecommerce.backend.repository.AddressRepository;
import com.smart_ecommerce.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressResponse create(
            UUID userId,
            AddressRequest request
    ) {

        User user = getUser(userId);

        boolean makeDefault =
                Boolean.TRUE.equals(request.isDefault());

        List<Address> existingAddresses =
                addressRepository.findByUserId(userId);

        if (existingAddresses.isEmpty()) {
            makeDefault = true;
        }

        if (makeDefault) {
            removeDefaultAddress(userId);
        }

        Address address = Address.builder()
                .user(user)
                .recipientName(request.recipientName())
                .phone(request.phone())
                .province(request.province())
                .district(request.district())
                .ward(request.ward())
                .addressDetail(request.addressDetail())
                .isDefault(makeDefault)
                .build();

        return toResponse(
                addressRepository.save(address)
        );
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(
            UUID userId
    ) {

        return addressRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse getById(
            UUID userId,
            UUID addressId
    ) {

        Address address = getUserAddress(
                userId,
                addressId
        );

        return toResponse(address);
    }

    public AddressResponse update(
            UUID userId,
            UUID addressId,
            AddressRequest request
    ) {

        Address address = getUserAddress(
                userId,
                addressId
        );

        boolean makeDefault =
                Boolean.TRUE.equals(request.isDefault());

        if (Boolean.TRUE.equals(address.getIsDefault())
                && !makeDefault) {

            makeDefault = true;
        }

        if (makeDefault) {
            removeDefaultAddress(userId);
        }

        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setProvince(request.province());
        address.setDistrict(request.district());
        address.setWard(request.ward());
        address.setAddressDetail(request.addressDetail());
        address.setIsDefault(makeDefault);

        return toResponse(
                addressRepository.save(address)
        );
    }

    public AddressResponse setDefault(
            UUID userId,
            UUID addressId
    ) {

        Address address = getUserAddress(
                userId,
                addressId
        );

        removeDefaultAddress(userId);

        address.setIsDefault(true);

        return toResponse(
                addressRepository.save(address)
        );
    }

    public void delete(
            UUID userId,
            UUID addressId
    ) {

        Address address = getUserAddress(
                userId,
                addressId
        );

        boolean wasDefault =
                Boolean.TRUE.equals(address.getIsDefault());

        addressRepository.delete(address);

        if (wasDefault) {

            List<Address> remaining =
                    addressRepository.findByUserId(userId);

            if (!remaining.isEmpty()) {
                remaining.get(0).setIsDefault(true);
                addressRepository.save(remaining.get(0));
            }
        }
    }

    private User getUser(UUID userId) {

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private Address getUserAddress(
            UUID userId,
            UUID addressId
    ) {

        return addressRepository
                .findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ"));
    }

    private void removeDefaultAddress(UUID userId) {

        addressRepository
                .findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(address -> {

                    address.setIsDefault(false);

                    addressRepository.save(address);
                });
    }

    private AddressResponse toResponse(
            Address address
    ) {

        return AddressResponse.builder()
                .id(address.getId())
                .userId(address.getUser().getId())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .district(address.getDistrict())
                .ward(address.getWard())
                .addressDetail(address.getAddressDetail())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .build();
    }
}
