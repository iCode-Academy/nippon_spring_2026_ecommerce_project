package mn.icode.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/auth/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/",
                                "/login",
                                "/register",
                                "/logout",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/error",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/auth/status")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/checkout", "/checkout/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/cart").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cart").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/orders", "/api/orders/*").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/orders", "/orders/*").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/cart/items").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PATCH, "/api/cart/items/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.DELETE, "/api/cart/items/**").hasRole("CUSTOMER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/orders/*/cancel",
                                "/orders/*/cancel")
                        .hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .permitAll()
                        .defaultSuccessUrl("/home", true)
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }
}
