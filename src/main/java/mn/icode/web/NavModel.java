package mn.icode.web;

/**
 * Navigation state exposed to every Thymeleaf view via {@link NavModelAdvice}.
 */
public record NavModel(boolean authenticated, boolean admin, String name, String currentPath) {

    public NavModel(boolean authenticated, boolean admin, String name) {
        this(authenticated, admin, name, "");
    }

    public NavModel {
        currentPath = currentPath == null ? "" : currentPath;
    }
}
