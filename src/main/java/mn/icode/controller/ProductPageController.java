package mn.icode.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.repository.CategoryRepository;
import mn.icode.service.ProductImageService;
import mn.icode.service.ProductService;
import mn.icode.service.ReviewService;

@Controller
public class ProductPageController {
	
	private final ProductService productService;
	private final CategoryRepository categoryRepository;
	private final ProductImageService productImageService;
	private final ReviewService reviewService;

	public ProductPageController(ProductService productService, CategoryRepository categoryRepository,
			ProductImageService productImageService, ReviewService reviewService) {
		this.productService = productService;
		this.categoryRepository = categoryRepository;
		this.productImageService = productImageService;
		this.reviewService = reviewService;
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

		boolean hasFilters = (keyword != null && !keyword.isBlank())
				|| categoryId != null
				|| minPrice != null
				|| maxPrice != null
				|| inStock != null
				|| !"default".equals(sort);
		long resultCount = 0;
		int currentPage = 0;
		int totalPages = 0;

		try {
			Page<Product> productPage = productService.filterProducts(keyword, categoryId, minPrice, maxPrice, inStock, pageable);
		
			model.addAttribute("products", productPage.getContent());
			currentPage = productPage.getNumber();
			totalPages = productPage.getTotalPages();
			resultCount = productPage.getTotalElements();
	        model.addAttribute("currentPage", currentPage);
	        model.addAttribute("totalPages", totalPages);
	        
		} catch (IllegalArgumentException e) {
			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("products", List.of());
		    model.addAttribute("currentPage", 0);
		    model.addAttribute("totalPages", 0);
		}
        
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        List<Category> categories = categoryRepository.findAll();
        String selectedCategoryName = categoryId == null ? null : categories.stream()
                .filter(category -> category.getId().equals(categoryId))
                .map(Category::getName)
                .findFirst()
                .orElse(null);
        int startPage = Math.max(0, currentPage - 2);
        int endPage = totalPages == 0 ? 0 : Math.min(totalPages - 1, startPage + 4);
        List<Integer> pageNumbers = IntStream.rangeClosed(startPage, endPage)
                .boxed()
                .toList();

        model.addAttribute("categories", categories);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("inStock", inStock);
        model.addAttribute("sort", sort);
        model.addAttribute("resultCount", resultCount);
        model.addAttribute("hasFilters", hasFilters);
        model.addAttribute("selectedCategoryName", selectedCategoryName);
        model.addAttribute("pageNumbers", pageNumbers);

		return "products/list";
	}

	@GetMapping("/products/{id}")
	public String showProduct(@PathVariable Long id, Model model) {
		Product product = productService.findActiveProductById(id);

		model.addAttribute("product", product);
		model.addAttribute("images", productImageService.findByProductId(id));
		model.addAttribute("reviewSummary", reviewService.getPublishedSummary(id));

		return "products/detail";
	}
}
