package mn.icode.dto;

import java.math.BigDecimal;
import java.util.List;

public class CheckoutResponse {

    private boolean success;
    private List<String> errors;
    private CartResponse cart;

    public CheckoutResponse() {}

    public CheckoutResponse(boolean success, List<String> errors, CartResponse cart) {
        this.success = success;
        this.errors = errors;
        this.cart = cart;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public CartResponse getCart() {
        return cart;
    }

    public void setCart(CartResponse cart) {
        this.cart = cart;
    }
}