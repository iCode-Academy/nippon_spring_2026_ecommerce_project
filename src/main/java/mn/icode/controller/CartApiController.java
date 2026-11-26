package mn.icode.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mn.icode.dto.AddCartItemRequest;
import mn.icode.dto.CartResponse;
import mn.icode.dto.UpdateCartItemRequest;
import mn.icode.service.CartService;

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

    @PatchMapping(value = "/items/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CartResponse updateItemQuantity(@Valid @RequestBody UpdateCartItemRequest request,
                                           @PathVariable long itemId, Authentication authentication) {
        return cartService.updateItemQuantity(authentication.getName(), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse deleteItem(@PathVariable Long itemId, Authentication authentication) {
        return cartService.deleteItem(authentication.getName(), itemId);
    }
}
