package mn.icode.controller;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mn.icode.dto.ProductResponse;
import mn.icode.entity.Category;
import mn.icode.entity.Product;
import mn.icode.exception.ResourceNotFoundException;
import mn.icode.service.ProductService;

@WebMvcTest(ProductApiController.class)
public class ProductApiControllerTest {
        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProductService productService;

        @Test
        void listProducts_returnsEmptyPage() throws Exception {
                when(productService.findActiveProducts(any()))
                                .thenReturn(new PageImpl<>(List.<Product>of(), PageRequest.of(0, 10), 0));

                mockMvc.perform(get("/api/products"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content").isEmpty())
                                .andExpect(jsonPath("$.totalElements").value(0));

        }

        @Test
        void listProducts_rejectsPageSizeAboveMaximum() throws Exception {
                mockMvc.perform(get("/api/products")
                                .param("page", "0")
                                .param("size", "100"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.title").value("Invalid request"))
                                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void getProductById_returnsActiveProduct() throws Exception {

                ProductResponse response = new ProductResponse(
                                1L,
                                "Laptop",
                                "Gaming laptop",
                                new BigDecimal("1200.00"),
                                5,
                                true,
                                2L,
                                "Electronics");

                Category category = org.mockito.Mockito.mock(Category.class);
                Product product = org.mockito.Mockito.mock(Product.class);

                when(product.getId()).thenReturn(response.id());
                when(product.getName()).thenReturn(response.name());
                when(product.getDescription()).thenReturn(response.description());
                when(product.getPrice()).thenReturn(response.price());
                when(product.getStockQuantity()).thenReturn(response.stockQuantity());
                when(product.isActive()).thenReturn(response.active());
                when(product.getCategory()).thenReturn(category);
                when(category.getId()).thenReturn(response.categoryId());
                when(category.getName()).thenReturn(response.categoryName());

                when(productService.findActiveProductById(1L))
                                .thenReturn(product);

                mockMvc.perform(get("/api/products/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.name").value("Laptop"))
                                .andExpect(jsonPath("$.description").value("Gaming laptop"))
                                .andExpect(jsonPath("$.price").value(1200.00))
                                .andExpect(jsonPath("$.stockQuantity").value(5))
                                .andExpect(jsonPath("$.active").value(true))
                                .andExpect(jsonPath("$.categoryId").value(2))
                                .andExpect(jsonPath("$.categoryName").value("Electronics"));
        }

        @Test
        void getProductById_returns404WhenProductDoesNotExist() throws Exception {

                when(productService.findActiveProductById(9999L))
                                .thenThrow(new ResourceNotFoundException("Product not found"));

                mockMvc.perform(get("/api/products/9999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.title").value("Resource not found"))
                                .andExpect(jsonPath("$.detail").value("Product not found"));
        }

        @Test
        void listProducts_passesCorrectPaginationToService() throws Exception {

                when(productService.findActiveProducts(any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.<Product>of()));

                mockMvc.perform(get("/api/products")
                                .param("page", "2")
                                .param("size", "5"))
                                .andExpect(status().isOk());

                ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

                verify(productService).findActiveProducts(captor.capture());

                Pageable pageable = captor.getValue();

                assertEquals(2, pageable.getPageNumber());
                assertEquals(5, pageable.getPageSize());
                assertEquals(
                                Sort.Direction.ASC,
                                pageable.getSort().getOrderFor("id").getDirection());
        }

        @Test
        void listProducts_rejectsNegativePageNumber() throws Exception {

                mockMvc.perform(get("/api/products")
                                .param("page", "-1")
                                .param("size", "10"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.title").value("Invalid request"));
        }

        @Test
        void listProducts_rejectsZeroPageSize() throws Exception {

                mockMvc.perform(get("/api/products")
                                .param("page", "0")
                                .param("size", "0"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.title").value("Invalid request"));
        }

        @Test
        void listProducts_allowsAnonymousUsers() throws Exception {

                when(productService.findActiveProducts(any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.<Product>of()));

                mockMvc.perform(get("/api/products")
                                .with(anonymous()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        void getProductById_allowsAnonymousUsers() throws Exception {

                Category category = mock(Category.class);
                Product product = mock(Product.class);

                when(product.getId()).thenReturn(1L);
                when(product.getName()).thenReturn("Laptop");
                when(product.getPrice()).thenReturn(new BigDecimal("1200.00"));
                when(product.getStockQuantity()).thenReturn(5);
                when(product.isActive()).thenReturn(true);
                when(product.getCategory()).thenReturn(category);

                when(category.getId()).thenReturn(2L);
                when(category.getName()).thenReturn("Electronics");

                when(productService.findActiveProductById(1L))
                                .thenReturn(product);

                mockMvc.perform(get("/api/products/1")
                                .with(anonymous()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.name").value("Laptop"))
                                .andExpect(jsonPath("$.active").value(true));
        }

}
