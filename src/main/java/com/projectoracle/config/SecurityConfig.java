package com.projectoracle.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Note: In production, enable CSRF
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    new AntPathRequestMatcher("/"),
                    new AntPathRequestMatcher("/login"),
                    new AntPathRequestMatcher("/login.html"),
                    new AntPathRequestMatcher("/index.html"),
                    new AntPathRequestMatcher("/dashboard.html"),
                    new AntPathRequestMatcher("/test-management.html"),
                    new AntPathRequestMatcher("/css/**"),
                    new AntPathRequestMatcher("/js/**"),
                    new AntPathRequestMatcher("/images/**"),
                    new AntPathRequestMatcher("/api/v1/health"),
                    new AntPathRequestMatcher("/api/v1/**"),  // Allow all API access for now
                    new AntPathRequestMatcher("/h2-console/**"),
                    new AntPathRequestMatcher("/swagger-ui/**"),
                    new AntPathRequestMatcher("/v3/api-docs/**"),
                    new AntPathRequestMatcher("/static/**"),
                    new AntPathRequestMatcher("/webjars/**")
                ).permitAll()
                .requestMatchers(
                    new AntPathRequestMatcher("/management/**")
                ).hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")  // Changed from /login.html to /login
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard.html", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll())
            .headers(headers -> headers.frameOptions().disable()); // For H2 console

        return http.build();
    }
}