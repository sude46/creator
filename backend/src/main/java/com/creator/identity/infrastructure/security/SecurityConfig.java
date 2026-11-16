package com.creator.identity.infrastructure.security;

import com.creator.identity.application.AccountQueryService;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthRateLimiter authRateLimiter(Clock clock, AuthRateLimitProperties properties) {
        return new AuthRateLimiter(clock, properties);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityHandlers handlers,
            AccountQueryService accounts, SecurityResponseWriter writer, AuthRateLimiter limiter,
            AuthRateLimitProperties properties) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));
        http.formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                .successHandler(handlers).failureHandler(handlers).permitAll());
        http.logout(logout -> logout.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.addFilterBefore(new ActiveAccountFilter(accounts, writer), AuthorizationFilter.class);
        http.addFilterBefore(new AuthRateLimitFilter(limiter, writer, properties), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
