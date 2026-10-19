package mn.icode.web;

/**
 * Navigation state exposed to every Thymeleaf view via {@link NavModelAdvice}.
 */
public record NavModel(boolean authenticated, boolean admin, String name) {
}
