package mn.icode.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
