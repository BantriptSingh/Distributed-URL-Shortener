package com.shorty.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, TokenAuthFilter tokenAuthFilter, com.shorty.ratelimit.RateLimitFilter rateLimitFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/s/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls/*/unlock")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/*/qr.png")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/analytics/live")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/*/analytics")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/*/events")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/urls/*")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.getWriter().write("{\"code\":\"unauthorized\",\"message\":\"Authentication required\"}");
                }))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, TokenAuthFilter.class);
        return http.build();
    }
}
