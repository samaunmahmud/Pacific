package com.pacific.marketplace.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pacific.marketplace.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless bearer-token API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/admin/login", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/verify-email")
                        .permitAll()
                        // Stripe calls this itself (no login); the handler verifies the request's signature.
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        // Public storefront reads. A bearer token is still honoured if sent, so
                        // the review list can show the viewer's own vote and their own reviews.
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*",
                                "/api/products/*/reviews", "/api/products/*/questions", "/api/categories",
                                "/api/sellers/*", "/api/images/*").permitAll()
                        // Approved sellers and admins upload product photos; the service checks seller approval.
                        .requestMatchers(HttpMethod.POST, "/api/images").hasAnyRole("CUSTOMER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Sellers are ordinary customer accounts; approval is checked per request in the service.
                        .requestMatchers("/api/cart/**", "/api/orders/**", "/api/payments/**", "/api/me/**",
                                "/api/reviews/**", "/api/wishlist/**", "/api/seller/**", "/api/sellers/**")
                        .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews", "/api/products/*/questions")
                        .hasRole("CUSTOMER")
                        // Admins moderate Q&A too, so they may answer and delete.
                        .requestMatchers("/api/questions/**", "/api/answers/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(AppProperties props) {
        return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Besides the signature and expiry, a token is only good while its password version is still the account's
     * current one, so changing or resetting a password signs out every other session (and a stolen token stops
     * working). One primary-key lookup per request.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> passwordVersion = jwt -> {
            int claimed = jwt.getClaim("pwv") instanceof Number n ? n.intValue() : 0;
            try {
                if (users.findPasswordVersionById(Long.valueOf(jwt.getSubject())).filter(v -> v == claimed).isPresent()) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (NumberFormatException ignored) {
                // falls through to the failure below
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "Your session has ended. Please sign in again.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), passwordVersion));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
