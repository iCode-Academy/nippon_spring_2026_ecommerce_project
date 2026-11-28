package mn.icode.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class ProfileAuthenticationService {

    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository;

    public ProfileAuthenticationService(UserDetailsService userDetailsService,
                                        SecurityContextRepository securityContextRepository) {
        this.userDetailsService = userDetailsService;
        this.securityContextRepository = securityContextRepository;
    }

    /** Keep the current session logged in when the login email changes. */
    public void refreshAfterEmailChange(String previousEmail, String updatedEmail,
                                        HttpServletRequest request, HttpServletResponse response) {
        if (previousEmail.equals(updatedEmail)) {
            return;
        }

        UserDetails details = userDetailsService.loadUserByUsername(updatedEmail);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, details.getPassword(), details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
