package mn.icode.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavModelAdvice {

    @ModelAttribute("nav")
    public NavModel nav(HttpServletRequest request) {
        boolean authenticated = request.getUserPrincipal() != null;
        boolean admin = request.isUserInRole("ADMIN");
        String name = authenticated ? request.getRemoteUser() : null;
        return new NavModel(authenticated, admin, name);
    }
}
