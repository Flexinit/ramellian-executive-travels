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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                        .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/users/*").authenticated()
                        .requestMatchers("/api/v1/documents/me").hasAuthority("DOCUMENT_READ")
                        .requestMatchers(HttpMethod.POST, "/api/v1/documents").hasAuthority("DOCUMENT_UPLOAD")
                        .requestMatchers("/api/v1/documents/admin/**").hasAuthority("DOCUMENT_REVIEW")
                        .requestMatchers("/api/v1/chauffeur/**").hasRole("CHAUFFEUR")
                        .requestMatchers("/api/v1/owner/**").hasRole("OWNER")
                        .requestMatchers("/api/v1/renter/**").hasRole("RENTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/bookings/**").hasAuthority("BOOKING_DELETE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/vehicles/**").hasAuthority("VEHICLE_DELETE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/payments/**").hasAuthority("PAYMENT_DELETE")
                        .requestMatchers("/api/v1/bookings/create").hasAuthority("BOOKING_CREATE")
                        .requestMatchers("/api/v1/bookings/reports/admin").hasAuthority("BOOKING_MANAGE")
                        .requestMatchers("/api/v1/bookings/reports/**").hasAuthority("BOOKING_READ")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*/status")
                                .hasAnyAuthority("BOOKING_UPDATE", "BOOKING_MANAGE", "BOOKING_READ")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*/chauffeur")
                                .hasAnyAuthority("BOOKING_MANAGE", "BOOKING_UPDATE")
                        .requestMatchers("/api/v1/bookings/**").hasAuthority("BOOKING_READ")
                        .requestMatchers("/api/v1/vehicles/create").hasAuthority("VEHICLE_CREATE")
                        .requestMatchers("/api/v1/vehicles/*/photos").hasAuthority("VEHICLE_UPDATE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews/**").hasAuthority("REVIEW_CREATE")
                        .requestMatchers("/api/v1/reviews/**").hasAuthority("REVIEW_READ")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/vehicles/**").hasAnyAuthority("VEHICLE_UPDATE", "VEHICLE_MANAGE")
                        .requestMatchers("/api/v1/vehicles/**").hasAuthority("VEHICLE_READ")
                        .requestMatchers("/api/v1/payments/create").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/payments/bookings/*/confirm").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/payments/pesapal/bookings/*/checkout").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/payments/pesapal/bookings/*/status").hasAuthority("PAYMENT_READ")
                        .requestMatchers("/api/v1/payments/pesapal/ipn").permitAll()
                        .requestMatchers("/api/v1/payments/bookings/*/contract/draft").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/payments/bookings/*/contract/accept").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/payments/contracts/*/sign").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers("/api/v1/contract-templates/owner", "/api/v1/contract-templates/mine")
                                .hasAuthority("VEHICLE_UPDATE")
                        .requestMatchers("/api/v1/contract-templates/admin/**")
                                .hasAuthority("BOOKING_MANAGE")
                        .requestMatchers("/api/v1/payments/**").hasAuthority("PAYMENT_MANAGE")
                        .anyRequest().authenticated())
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
}
