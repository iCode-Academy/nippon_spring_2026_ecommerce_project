package mn.icode.controller;

import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mn.icode.dto.CartResponse;
import mn.icode.dto.AddCartItemRequest;
import mn.icode.service.CartService;
import jakarta.validation.Valid;

@RestController 
@RequestMapping("/api/cart")
public class CartApiController {
    private final CartService cartService;

    public CartApiController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(Authentication authentication) {
        return cartService.getCart(authentication.getName());
    }

    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request, Authentication authentication) {
        return cartService.addItem(authentication.getName(), request.productId(), request.quantity());
    }
}
