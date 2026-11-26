package mn.icode.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setDetail("A valid JSON request body is required.");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleNotFound(ResourceNotFoundException exception,
            HttpServletRequest request) {
        if (prefersHtml(request)) {
            return errorView("error/404", HttpStatus.NOT_FOUND, request);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Resource not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseBody
    public ProblemDetail handleConflict(ConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Conflict");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception exception, HttpServletRequest request) {
        if (prefersHtml(request)) {
            return errorView("error/500", HttpStatus.INTERNAL_SERVER_ERROR, request);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal server error");
        problem.setDetail("An unexpected error occurred.");

        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {

        if (prefersHtml(request)) {
            return errorView("error/404", HttpStatus.NOT_FOUND, request);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Resource not found");
        problem.setDetail("The requested resource was not found.");

        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ProblemDetail handleAccessDenied(
            AccessDeniedException exception) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);

        problem.setTitle("Forbidden");
        problem.setDetail(
                "You do not have permission to access this resource.");

        return problem;
    }

    // Хэрэглэгчийн хүсэлт HTML хүлээж байвал error шалгуураар HTML хуудас үзүүлнэ
    private boolean prefersHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private ModelAndView errorView(String viewName, HttpStatus status,
            HttpServletRequest request) {
        ModelAndView modelAndView = new ModelAndView(viewName);
        modelAndView.setStatus(status);
        boolean authenticated = request.getUserPrincipal() != null;
        modelAndView.addObject("nav", new mn.icode.web.NavModel(
                authenticated,
                request.isUserInRole("ADMIN"),
                authenticated ? request.getRemoteUser() : null,
                request.getRequestURI()));
        return modelAndView;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage());

        problem.setTitle("Invalid request");

        return ResponseEntity.badRequest().body(problem);
    }

}
