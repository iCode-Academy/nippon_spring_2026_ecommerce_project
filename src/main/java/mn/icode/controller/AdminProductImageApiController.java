package mn.icode.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mn.icode.dto.ProductImageForm;
import mn.icode.dto.ProductImageOrderForm;
import mn.icode.dto.ProductImageResponse;
import mn.icode.service.ProductImageService;

@RestController
@RequestMapping("/api/admin/products/{productId}/images")
public class AdminProductImageApiController {
    private final ProductImageService imageService;

    public AdminProductImageApiController(ProductImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping 
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return imageService.findByProductId(productId).stream().map(ProductImageResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse add(@PathVariable Long productId, @Valid @RequestBody ProductImageForm form) {
        return ProductImageResponse.from(imageService.addImage(productId, form.getImageUrl()));
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
        @PathVariable Long productId,
        @Valid @RequestBody ProductImageOrderForm form) {

        imageService.reorderImages(productId, form.getImageIds());
    }      

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long productId, @PathVariable Long imageId) {
        imageService.deleteImage(productId, imageId);
    }
}
