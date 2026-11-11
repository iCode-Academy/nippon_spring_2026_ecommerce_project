package mn.icode.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import mn.icode.entity.Product;
import mn.icode.repository.CategoryRepository;
import mn.icode.service.ProductService;

@Controller
public class ProductPageController {
	
	private final ProductService productService;
	private final CategoryRepository categoryRepository;
	
	public ProductPageController(ProductService productService, CategoryRepository categoryRepository) {
		this.productService = productService;
		this.categoryRepository = categoryRepository;
	}
	
	private static final int PAGE_SIZE = 12;
	
	@GetMapping("/products")
	public String listProducts(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) Boolean inStock,
			@RequestParam(defaultValue = "default") String sort,
			Model model) {
		
		Sort sorting = Sort.by("id").ascending();
		
		if ("priceAsc".equals(sort)) {
		    sorting = Sort.by("price").ascending()
		    		.and(Sort.by("id").ascending());
		} else if ("priceDesc".equals(sort)) {
		    sorting = Sort.by("price").descending()
		    		.and(Sort.by("id").ascending());
		} else if ("newest".equals(sort)) {
		    sorting = Sort.by("createdAt").descending()
		            .and(Sort.by("id").descending());
		}
		
		Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, sorting);
		
		try {
			Page<Product> productPage = productService.filterProducts(keyword, categoryId, minPrice, maxPrice, inStock, pageable);
		
			model.addAttribute("products", productPage.getContent());
	        model.addAttribute("currentPage", productPage.getNumber());
	        model.addAttribute("totalPages", productPage.getTotalPages());
	        
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("products", List.of());
		    model.addAttribute("currentPage", 0);
		    model.addAttribute("totalPages", 0);
		}
        
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("inStock", inStock);
        model.addAttribute("sort", sort);
		
		return "products/list";
	}
}
