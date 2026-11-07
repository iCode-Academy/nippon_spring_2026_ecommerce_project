package mn.icode.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import mn.icode.dto.ProductResponse;
import mn.icode.entity.Product;
import mn.icode.service.ProductService;

@RestController 
@RequestMapping("/api/products")
public class ProductApiController {
    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> listProducts(@RequestParam(defaultValue="0") int page,
                                              @RequestParam(defaultValue="10") int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page must be >= 0 and size must be between 1 and 50");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("id").ascending());
    
        return productService.findActiveProducts(pageable).map(ProductResponse::from);
    }

    @GetMapping ("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        Product product = productService.findActiveProductById(id);
        return ProductResponse.from(product);
    }

}
