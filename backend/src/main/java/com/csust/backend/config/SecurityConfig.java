package com.csust.backend.config;

import com.csust.backend.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (response.isCommitted()) return;
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"Access Denied\"}");
                }))
                // 在 AuthorizationFilter 之前插入自定义过滤器，捕获 AuthorizationDeniedException
                // 避免 SSE 流等场景下 response 已提交后 Security 抛出异常
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected boolean shouldNotFilterAsyncDispatch() {
                        return false; // ASYNC 分发时也执行此过滤器，兜底捕获异常
                    }

                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                    FilterChain chain) throws ServletException, IOException {
                        try {
                            chain.doFilter(request, response);
                        } catch (AuthorizationDeniedException e) {
                            if (!response.isCommitted()) {
                                log.warn("Authorization Denied [{} {}]: {}", request.getMethod(),
                                        request.getRequestURI(), e.getMessage());
                                response.setStatus(403);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"code\":403,\"message\":\"Access Denied\"}");
                            }
                        }
                    }
                }, AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // SSE/Streaming 的 ASYNC 分发不再二次鉴权（初始 REQUEST 分发已鉴权）
                        // ERROR 分发一并放行，防止异步场景下错误页渲染失败
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/users/reset-password/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/doc.html").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/{id:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/topics").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{id:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{id:[0-9]+}/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{id:[0-9]+}/followings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/messages/online/{userId:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notifications/unread-count").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notifications/unread-count-by-type").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/messages/unread-count").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
