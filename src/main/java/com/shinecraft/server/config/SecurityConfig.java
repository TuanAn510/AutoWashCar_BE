package com.shinecraft.server.config;

import com.shinecraft.server.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/signup",
                                "/api/auth/signin",
                                "/api/auth/refresh",
                                "/api/auth/refresh-token",
                                "/api/auth/logout",
                                "/api/auth/signout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/project-report").permitAll()
                        .requestMatchers(HttpMethod.GET, "/project-report.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/survey/logs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                        .requestMatchers("/api/payment/vnpay/return", "/api/payment/vnpay/ipn",
                                "/api/payment/momo/return", "/api/payment/momo/ipn").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vehicle-brands/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/services/active", "/api/service-categories/active").permitAll()
                        // Ảnh/tài liệu tải lên được phục vụ tĩnh — trình duyệt không
                        // gửi Authorization header với <img>/<a> nên phải permitAll
                        // nếu không mọi ảnh (xe, minh chứng) đều trả 403.
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/vehicle-access-requests")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/vehicle-access-requests/*/approve",
                                "/api/vehicle-access-requests/*/reject")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/overview", "/api/reports/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/appointments", "/api/service-histories")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*", "/api/users/staffs/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/me").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/users/*")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/*/assign-staff", "/api/appointments/*/reschedule", "/api/appointments/*/cancel")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/service-histories/*")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/service-histories/*")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/loyalty/customers", "/api/loyalty/customers/**")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/membership-tiers", "/api/rewards")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/membership-tiers/**", "/api/rewards/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/rewards/redemptions/*/use")
                        .hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/membership-tiers/**", "/api/rewards/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/membership-tiers/**", "/api/rewards/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/appointments/staff/**", "/api/service-histories/staff/**").hasAnyAuthority("ROLE_STAFF", "ROLE_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(splitCsv(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> splitCsv(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
