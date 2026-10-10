package mn.icode.exception;

import java.util.LinkedHashMap;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice 
public class GlobalExceptionHandler {
    
    @ExceptionHandler (MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> 
            errors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        problem.setProperty("errors", errors);

        return problem;
        }

        @ExceptionHandler (ResourceNotFoundException.class)
        public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

            problem.setTitle("Resource not found");
            problem.setDetail(exception.getMessage());

            return problem;
        }

        @ExceptionHandler(ConflictException.class)
        public ProblemDetail handleConflict(ConflictException exception) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

            problem.setTitle("Conflict");
            problem.setDetail(exception.getMessage());

            return problem;
        } 

        @ExceptionHandler(Exception.class)
        public ProblemDetail handleUnexpected(Exception exception) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            problem.setTitle("Internal server error");
            problem.setDetail("An unexpected error occurred.");

            return problem;
        }
        
        @ExceptionHandler(NoResourceFoundException.class)
        public ProblemDetail handleNoResourceFound(
                NoResourceFoundException exception) {

            ProblemDetail problem =
                    ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

            problem.setTitle("Resource not found");
            problem.setDetail("The requested resource was not found.");

            return problem;
        }
        
        @ExceptionHandler(AccessDeniedException.class)
        public ProblemDetail handleAccessDenied(
                AccessDeniedException exception) {

            ProblemDetail problem =
                    ProblemDetail.forStatus(HttpStatus.FORBIDDEN);

            problem.setTitle("Forbidden");
            problem.setDetail(
                    "You do not have permission to access this resource."
            );

            return problem;
        }
        
}
