package com.smart_ecommerce.backend.config;

import com.smart_ecommerce.backend.security.JwtAuthenticationFilter;
import com.smart_ecommerce.backend.security.RestSecurityExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestSecurityExceptionHandler restSecurityExceptionHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS must contain exact origins and cannot contain '*'");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With",
                "Idempotency-Key", "X-Payment-Signature"
        ));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restSecurityExceptionHandler)
                        .accessDeniedHandler(restSecurityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/users/register", "/api/users/login", "/api/users/google")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/callback/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/media/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/*/reply").permitAll()
                        .requestMatchers("/api/test")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        // Google account linking is available only with a valid PPee JWT.
                        .requestMatchers("/api/users/google/link").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                        // Shop registration requires an existing CUSTOMER account.
                        // No shop id from the client is trusted for owner-specific operations.
                        .requestMatchers(HttpMethod.POST, "/api/seller/shop").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/seller/shop").hasAnyRole("CUSTOMER", "SELLER")
                        .requestMatchers(HttpMethod.PUT, "/api/seller/shop").hasAnyRole("CUSTOMER", "SELLER")
                        .requestMatchers("/api/seller/products", "/api/seller/products/**").hasRole("SELLER")
                        .requestMatchers("/api/seller/orders", "/api/seller/orders/**").hasRole("SELLER")
                        .requestMatchers("/api/seller/reviews", "/api/seller/reviews/**").hasRole("SELLER")
                        .requestMatchers("/api/seller/coupons", "/api/seller/coupons/**").hasRole("SELLER")
                        .requestMatchers("/api/seller/notifications", "/api/seller/notifications/**").hasRole("SELLER")
                        .requestMatchers("/api/seller/returns", "/api/seller/returns/**").hasRole("SELLER")
                        .requestMatchers("/api/returns/mine").hasRole("CUSTOMER")
                        .requestMatchers("/api/admin/console/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/shops/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/returns", "/api/admin/returns/**").hasRole("ADMIN")
                        .requestMatchers("/api/staff/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/analytics/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/recommendations/events")
                        .permitAll()
                        .requestMatchers("/api/data-mining/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/recommendations/products/*/related",
                                "/api/recommendations/popular")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/personalized")
                        .hasRole("CUSTOMER")

                        // Read-only assistant: public catalog/FAQ, private orders scoped inside service.
                        .requestMatchers(HttpMethod.POST, "/api/assistant/chat").permitAll()

                        // Private analytics routes must precede the public catalog matcher.
                        .requestMatchers(HttpMethod.GET, "/api/products/*/views")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products/*/views")
                        .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/products/status/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET,
                                "/api/products/**", "/api/categories/**",
                                "/api/product-variants/**")
                        .permitAll()

                        // Product review lists are public; personal review routes are not.
                        .requestMatchers(HttpMethod.GET,
                                "/api/reviews/products/*/mine", "/api/reviews/products/*/eligibility",
                                "/api/reviews/mine")
                        .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/reviews/products/*")
                        .permitAll()
                        .requestMatchers("/api/reviews/**")
                        .hasRole("CUSTOMER")

                        .requestMatchers(HttpMethod.POST, "/api/coupons/validate")
                        .hasRole("CUSTOMER")

                        // Staff can maintain products and variants; taxonomy and promotions stay admin-only.
                        .requestMatchers(HttpMethod.POST,
                                "/api/products/**", "/api/product-variants/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/products/**", "/api/product-variants/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/products/**", "/api/product-variants/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/products/**", "/api/product-variants/**")
                        .hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers(HttpMethod.POST,
                                "/api/categories/**", "/api/coupons/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/categories/**", "/api/coupons/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/categories/**", "/api/coupons/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/categories/**", "/api/coupons/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/orders/*/status")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET,
                                "/api/orders/management", "/api/orders/management/**")
                        .hasAnyRole("ADMIN", "STAFF")

                        .requestMatchers(HttpMethod.POST, "/api/shipping/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/shipping/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/shipping/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.DELETE, "/api/shipping/**")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/shipping")
                        .hasAnyRole("ADMIN", "STAFF")
                        .requestMatchers("/api/shipping/**")
                        .hasAnyRole("CUSTOMER", "ADMIN", "STAFF")
                        .requestMatchers("/api/coupons/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/payments/**")
                        .hasAnyRole("CUSTOMER", "ADMIN")

                        // Customer APIs always operate on the authenticated user's data.
                        .requestMatchers("/api/cart/**", "/api/addresses/**",
                                "/api/orders/**", "/api/favorites/**",
                                "/api/search-history/**", "/api/views/mine/**",
                                "/api/views/mine")
                        .hasRole("CUSTOMER")

                        .anyRequest().hasAnyRole("CUSTOMER", "ADMIN"))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
