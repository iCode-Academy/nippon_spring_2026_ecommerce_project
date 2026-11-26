package mn.icode.dto;

import jakarta.validation.constraints.NotNull;

public class CheckoutRequest {

    @NotNull(message = "Delivery address is required")
    private Long deliveryAddressId;

    public CheckoutRequest() {}

    public Long getDeliveryAddressId() {
        return deliveryAddressId;
    }

    public void setDeliveryAddressId(Long deliveryAddressId) {
        this.deliveryAddressId = deliveryAddressId;
    }
}