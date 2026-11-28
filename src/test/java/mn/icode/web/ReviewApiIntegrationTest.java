package mn.icode.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.transaction.Transactional;
import mn.icode.entity.Category;
import mn.icode.entity.Order;
import mn.icode.entity.OrderItem;
import mn.icode.entity.Product;
import mn.icode.entity.Review;
import mn.icode.entity.User;
import mn.icode.model.OrderStatus;
import mn.icode.model.ReviewStatus;
import mn.icode.model.Role;
import mn.icode.repository.CategoryRepository;
import mn.icode.repository.OrderRepository;
import mn.icode.repository.ProductRepository;
import mn.icode.repository.ReviewRepository;
import mn.icode.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(new Category("Reviews " + UUID.randomUUID()));
    }

    private Product saveProduct(String name) {
        return productRepository.save(
                new Product(category, name, "Test product", new BigDecimal("9.99"), 10, true));
    }

    private User saveCustomer(String email) {
        User user = new User();
        user.setFirstName("First");
        user.setLastName("Last");
        user.setEmail(email);
        user.setPassword("secret");
        user.setRole(Role.CUSTOMER);
        return userRepository.save(user);
    }

    private Order saveDeliveredOrder(User customer, Product product) {
        Order order = new Order();
        order.setUser(customer);
        order.setShipCity("Ulaanbaatar");
        order.setShipDistrict("Sukhbaatar");
        order.setShipAddressLine("Peace Ave 1");
        order.setShipPhone("99112233");
        order.addItem(OrderItem.from(product, 1));
        order.recalcTotals();
        order.setStatus(OrderStatus.DELIVERED);
        return orderRepository.save(order);
    }

    private Review saveApprovedReview(User customer, Product product, int rating, String comment) {
        Review review = new Review(customer, product, rating, comment);
        ReflectionTestUtils.setField(review, "moderationStatus", ReviewStatus.APPROVED);
        return reviewRepository.save(review);
    }

    private String json(Integer rating, String comment) {
        return "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}";
    }

    // --- Listing and moderation visibility ---

    @Test
    void listReviews_returnsOnlyApprovedReviewsWithSummary() throws Exception {
        Product product = saveProduct("Reviewed Pen");
        String suffix = UUID.randomUUID().toString();

        saveApprovedReview(saveCustomer("a-" + suffix + "@example.com"), product, 5, "Excellent pen");
        saveApprovedReview(saveCustomer("b-" + suffix + "@example.com"), product, 3, "Average pen");
        reviewRepository.save(new Review(
                saveCustomer("c-" + suffix + "@example.com"), product, 1, "Pending review"));

        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews", hasSize(2)))
                .andExpect(jsonPath("$.summary.publishedReviewCount").value(2))
                .andExpect(jsonPath("$.summary.averageRating").value(4.0))
                .andExpect(jsonPath("$.currentUserReview", nullValue()))
                .andExpect(jsonPath("$.reviewEligible").value(false))
                .andExpect(content().string(containsString("Excellent pen")))
                .andExpect(content().string(containsString("Average pen")))
                .andExpect(content().string(not(containsString("Pending review"))));
    }

    @Test
    void listReviews_returnsEmptyStateWhenNoApprovedReviews() throws Exception {
        Product product = saveProduct("Unreviewed Pen");

        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews", hasSize(0)))
                .andExpect(jsonPath("$.summary.publishedReviewCount").value(0))
                .andExpect(jsonPath("$.summary.averageRating", nullValue()));
    }

    @Test
    void detailPage_listsApprovedReviewsAndShowsEmptyState() throws Exception {
        Product product = saveProduct("Detail Review Pen");
        String suffix = UUID.randomUUID().toString();
        saveApprovedReview(saveCustomer("visible-" + suffix + "@example.com"), product, 5, "Lovely pen");

        mockMvc.perform(get("/products/{id}", product.getId())
                        .with(user("browser-" + suffix + "@example.com").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(view().name("products/detail"))
                .andExpect(content().string(containsString("Lovely pen")))
                .andExpect(content().string(containsString("Published reviews")));
    }

    // --- Eligibility ---

    @Test
    void createReview_eligibleCustomer_createsPendingReview() throws Exception {
        Product product = saveProduct("Eligible Pen");
        String email = "eligible-" + UUID.randomUUID() + "@example.com";
        User customer = saveCustomer(email);
        saveDeliveredOrder(customer, product);

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user(email).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "Very nice pen")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));

        // Newly created review is not publicly visible until approved.
        mockMvc.perform(get("/api/products/{id}/reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews", hasSize(0)));

        // But the author can see their own pending review.
        mockMvc.perform(get("/api/products/{id}/reviews", product.getId())
                        .with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserReview.rating").value(4))
                .andExpect(jsonPath("$.currentUserReview.moderationStatus").value("PENDING"));
    }

    @Test
    void createReview_ineligibleCustomer_returnsConflict() throws Exception {
        Product product = saveProduct("Ineligible Pen");
        String email = "ineligible-" + UUID.randomUUID() + "@example.com";
        saveCustomer(email);

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user(email).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "I never bought this")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    // --- Duplicate rejection ---

    @Test
    void createReview_duplicateReview_returnsConflict() throws Exception {
        Product product = saveProduct("Duplicate Pen");
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        User customer = saveCustomer(email);
        saveDeliveredOrder(customer, product);

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user(email).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "First review")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user(email).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(5, "Second review")))
                .andExpect(status().isConflict());
    }

    // --- Validation ---

    @Test
    void createReview_invalidRating_returnsBadRequest() throws Exception {
        Product product = saveProduct("Rating Pen");

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user("valid-" + UUID.randomUUID() + "@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(0, "Zero stars")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    @Test
    void createReview_blankComment_returnsBadRequest() throws Exception {
        Product product = saveProduct("Blank Pen");

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user("blank-" + UUID.randomUUID() + "@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").exists());
    }

    @Test
    void createReview_tooLongComment_returnsBadRequest() throws Exception {
        Product product = saveProduct("Long Pen");
        String longComment = "a".repeat(1001);

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user("long-" + UUID.randomUUID() + "@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"" + longComment + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").exists());
    }

    // --- Ownership ---

    @Test
    void updateReview_owner_updatesAndResetsToPending() throws Exception {
        Product product = saveProduct("Owner Update Pen");
        String owner = "owner-" + UUID.randomUUID() + "@example.com";
        User ownerUser = saveCustomer(owner);
        saveDeliveredOrder(ownerUser, product);
        Review review = saveApprovedReview(ownerUser, product, 5, "Before edit");

        mockMvc.perform(put("/api/reviews/{id}", review.getId())
                        .with(user(owner).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(2, "After edit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").value("After edit"))
                .andExpect(jsonPath("$.moderationStatus").value("PENDING"));
    }

    @Test
    void updateReview_foreignCustomer_returnsForbidden() throws Exception {
        Product product = saveProduct("Foreign Update Pen");
        String owner = "owner-" + UUID.randomUUID() + "@example.com";
        User ownerUser = saveCustomer(owner);
        Review review = saveApprovedReview(ownerUser, product, 5, "Owner review");
        String intruder = "intruder-" + UUID.randomUUID() + "@example.com";
        saveCustomer(intruder);

        mockMvc.perform(put("/api/reviews/{id}", review.getId())
                        .with(user(intruder).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(1, "Hijacked")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReview_owner_returnsNoContent() throws Exception {
        Product product = saveProduct("Owner Delete Pen");
        String owner = "owner-" + UUID.randomUUID() + "@example.com";
        User ownerUser = saveCustomer(owner);
        Review review = saveApprovedReview(ownerUser, product, 5, "To delete");

        mockMvc.perform(delete("/api/reviews/{id}", review.getId())
                        .with(user(owner).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReview_foreignCustomer_returnsForbidden() throws Exception {
        Product product = saveProduct("Foreign Delete Pen");
        String owner = "owner-" + UUID.randomUUID() + "@example.com";
        User ownerUser = saveCustomer(owner);
        Review review = saveApprovedReview(ownerUser, product, 5, "Keep me");
        String intruder = "intruder-" + UUID.randomUUID() + "@example.com";
        saveCustomer(intruder);

        mockMvc.perform(delete("/api/reviews/{id}", review.getId())
                        .with(user(intruder).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- Authentication and CSRF ---

    @Test
    void createReview_unauthenticated_redirectsToLogin() throws Exception {
        Product product = saveProduct("Anonymous Pen");

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "Anonymous review")))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void createReview_missingCsrfToken_returnsForbidden() throws Exception {
        Product product = saveProduct("Csrf Pen");

        mockMvc.perform(post("/api/products/{id}/reviews", product.getId())
                        .with(user("csrf-" + UUID.randomUUID() + "@example.com").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(4, "No token")))
                .andExpect(status().isForbidden());
    }

    // --- Detail page interactive controls ---

    @Test
    void detailPage_eligibleCustomerSeesCreateForm() throws Exception {
        Product product = saveProduct("Form Pen");
        String email = "form-" + UUID.randomUUID() + "@example.com";
        User customer = saveCustomer(email);
        saveDeliveredOrder(customer, product);

        mockMvc.perform(get("/products/{id}", product.getId())
                        .with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Write a review")))
                .andExpect(content().string(not(containsString("Only customers with a delivered order"))));
    }

    @Test
    void detailPage_ineligibleCustomerSeesEligibilityNote() throws Exception {
        Product product = saveProduct("Ineligible Form Pen");
        String email = "inel-form-" + UUID.randomUUID() + "@example.com";
        saveCustomer(email);

        mockMvc.perform(get("/products/{id}", product.getId())
                        .with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Write a review"))))
                .andExpect(content().string(containsString(
                        "Only customers with a delivered order containing this product can write a review.")));
    }

    @Test
    void detailPage_ownerSeesEditForm() throws Exception {
        Product product = saveProduct("Edit Form Pen");
        String email = "edit-" + UUID.randomUUID() + "@example.com";
        User customer = saveCustomer(email);
        saveApprovedReview(customer, product, 4, "My existing review");

        mockMvc.perform(get("/products/{id}", product.getId())
                        .with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your review")))
                .andExpect(content().string(containsString("Save changes")))
                .andExpect(content().string(containsString("js-delete-review")));
    }
}
