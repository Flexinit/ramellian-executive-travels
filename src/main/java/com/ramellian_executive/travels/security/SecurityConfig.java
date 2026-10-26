package com.ramellian_executive.travels.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/vehicles", "/api/v1/vehicles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contract-templates/owners/*/active").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rental-terms", "/api/v1/rental-terms/category/**", "/api/v1/rental-terms/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/quote").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/booking-leads").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/bot/whatsapp/inbound").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/bot/status").permitAll()
                        .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/users/*").authenticated()
                        .requestMatchers("/api/v1/documents/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents").authenticated()
                        .requestMatchers("/api/v1/documents/admin/**").access(superAdminOr("hasAnyAuthority('DOCUMENT_REVIEW','BOOKING_MANAGE')"))
                        .requestMatchers("/api/v1/chauffeur/**").access(superAdminOr("hasRole('CHAUFFEUR')"))
                        .requestMatchers("/api/v1/owner/**").access(superAdminOr("hasRole('OWNER')"))
                        .requestMatchers("/api/v1/renter/**").access(superAdminOr("hasRole('RENTER')"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bookings/**").access(superAdminOr("hasAuthority('BOOKING_DELETE')"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vehicles/**").access(superAdminOr("hasAuthority('VEHICLE_DELETE')"))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/payments/**").access(superAdminOr("hasAuthority('PAYMENT_DELETE')"))
                        // Booking create/read: any authenticated user — service layer enforces ownership.
                        // Renters/owners have no explicit permissions by default so hasAuthority gates block them.
                        .requestMatchers("/api/v1/bookings/create").authenticated()
                        .requestMatchers("/api/v1/bookings/create-batch").authenticated()
                        .requestMatchers("/api/v1/payments/pesapal/booking-groups/*/checkout").authenticated()
                        .requestMatchers("/api/v1/bookings/reports/admin").access(superAdminOr("hasAuthority('BOOKING_MANAGE')"))
                        .requestMatchers("/api/v1/bookings/reports/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*/status").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*/chauffeur")
                                .access(superAdminOr("hasAnyAuthority('BOOKING_MANAGE','BOOKING_UPDATE')"))
                        .requestMatchers("/api/v1/bookings/**").authenticated()
                        .requestMatchers("/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/chat/**").authenticated()
                        .requestMatchers("/api/v1/vehicles/create", "/api/v1/vehicles/create-vehicle").access(superAdminOr("hasAuthority('VEHICLE_CREATE')"))
                        .requestMatchers("/api/v1/vehicles/*/photos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews/**").access(superAdminOr("hasAuthority('REVIEW_CREATE')"))
                        .requestMatchers("/api/v1/reviews/**").access(superAdminOr("hasAuthority('REVIEW_READ')"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/**").access(superAdminOr("hasAnyAuthority('VEHICLE_UPDATE','VEHICLE_MANAGE')"))
                        .requestMatchers("/api/v1/vehicles/**").access(superAdminOr("hasAuthority('VEHICLE_READ')"))
                        .requestMatchers("/api/v1/payments/create").authenticated()
                        .requestMatchers("/api/v1/payments/bookings/*/confirm").authenticated()
                        .requestMatchers("/api/v1/payments/pesapal/bookings/*/checkout").authenticated()
                        .requestMatchers("/api/v1/payments/pesapal/bookings/*/status").authenticated()
                        .requestMatchers("/api/v1/payments/pesapal/ipn").permitAll()
                        .requestMatchers("/api/v1/payments/bookings/*/contract/draft").authenticated()
                        .requestMatchers("/api/v1/payments/bookings/*/contract/accept").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/contracts/*/sign").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/contracts/*", "/api/v1/payments/contracts/*/document").authenticated()
                        .requestMatchers("/api/v1/contract-templates/owner", "/api/v1/contract-templates/mine")
                                .access(superAdminOr("hasAuthority('VEHICLE_UPDATE')"))
                        .requestMatchers("/api/v1/contract-templates/admin/**")
                                .access(superAdminOr("hasAuthority('BOOKING_MANAGE')"))
                        .requestMatchers("/api/v1/payments/**").access(superAdminOr("hasAuthority('PAYMENT_MANAGE')"))
                        .requestMatchers(HttpMethod.POST, "/api/v1/handovers/bookings/*/pickup",
                                "/api/v1/handovers/bookings/*/dropoff").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/handovers/bookings/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/booking-policies/bookings/*/cancel",
                                "/api/v1/booking-policies/bookings/*/postpone").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/booking-policies/bookings/*/effective").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/booking-policies/platform").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/booking-policies/platform").access(superAdminOr("hasAuthority('BOOKING_MANAGE')"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/booking-policies/owner").access(superAdminOr("hasAuthority('VEHICLE_UPDATE')"))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/booking-policies/owner").access(superAdminOr("hasAuthority('VEHICLE_UPDATE')"))
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform-settings").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/platform-settings").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/platform-settings/bot").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required or token has expired\",\"details\":{}}");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isBlank()).toList());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setExposedHeaders(java.util.List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private static AuthorizationManager<RequestAuthorizationContext> superAdminOr(String expression) {
        return new WebExpressionAuthorizationManager("hasRole('SUPER_ADMIN') or (" + expression + ")");
    }
}
