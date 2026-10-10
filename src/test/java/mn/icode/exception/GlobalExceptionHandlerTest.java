package mn.icode.exception;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import mn.icode.controller.RegistrationController;
import mn.icode.entity.User;
import mn.icode.service.UserService;

@WebMvcTest(RegistrationController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void invalidRegistrationReturnsBadRequestWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {
                    "firstName": "",
                    "lastName": "Smith",
                    "email": "not-an-email",
                    "password": ""
                }
                """)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.errors.firstName")
                        .value("First name is required"))
                .andExpect(jsonPath("$.errors.email")
                        .value("Email must be valid"))
                .andExpect(jsonPath("$.errors.password")
                        .value("Password is required"));
    }

    @Test
    void conflictReturns409ProblemDetail() throws Exception {

        when(userService.registerUser(any(User.class)))
                .thenThrow(new ConflictException(
                        "Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "firstName": "Nora",
                            "lastName": "Test",
                            "email": "nora@example.com",
                            "password": "password123"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Conflict"))
                .andExpect(jsonPath("$.status")
                        .value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Email is already registered"));
    }
    
    @Test
    void missingResourceReturns404ProblemDetail() throws Exception {

        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                        .value("Resource not found"))
                .andExpect(jsonPath("$.status")
                        .value(404))
                .andExpect(jsonPath("$.detail")
                        .value("The requested resource was not found."));
    }

    @Test
    void unexpectedErrorReturnsSafe500ProblemDetail() throws Exception {

    	    when(userService.registerUser(any(User.class)))
    	            .thenThrow(new RuntimeException(
    	                    "SECRET DATABASE ERROR"
    	            ));

    	    mockMvc.perform(post("/api/auth/register")
    	            .contentType(MediaType.APPLICATION_JSON)
    	            .content("""
    	                {
    	                    "firstName": "Nora",
    	                    "lastName": "Test",
    	                    "email": "nora@example.com",
    	                    "password": "password123"
    	                }
    	                """))
    	            .andExpect(status().isInternalServerError())
    	            .andExpect(jsonPath("$.title")
    	                    .value("Internal server error"))
    	            .andExpect(jsonPath("$.status")
    	                    .value(500))
    	            .andExpect(jsonPath("$.detail")
    	                    .value("An unexpected error occurred."))
    	            .andExpect(content().string(
    	                    not(containsString("SECRET DATABASE ERROR"))
    	            ));
    	
    }
    
    @Test
    void forbiddenErrorReturns403ProblemDetail() {

        GlobalExceptionHandler handler =
                new GlobalExceptionHandler();

        ProblemDetail problem =
                handler.handleAccessDenied(
                        new AccessDeniedException("SECRET SECURITY DETAIL")
                );

        assertEquals(403, problem.getStatus());
        assertEquals("Forbidden", problem.getTitle());
        assertEquals(
                "You do not have permission to access this resource.",
                problem.getDetail()
        );

        assertFalse(
                problem.getDetail().contains("SECRET SECURITY DETAIL")
        );
    }
        
 
}
