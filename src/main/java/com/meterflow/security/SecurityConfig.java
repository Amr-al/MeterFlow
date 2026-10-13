package com.meterflow.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationProvider apiKeyAuthenticationProvider
    ) throws Exception {

        AuthenticationManager authenticationManager =
                new ProviderManager(apiKeyAuthenticationProvider);

        AuthenticationEntryPoint apiAuthenticationEntryPoint =
                (request, response, authException) ->
                        response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Unauthorized"
                        );

        ApiKeyAuthenticationFilter apiKeyFilter =
                new ApiKeyAuthenticationFilter(
                        authenticationManager,
                        apiAuthenticationEntryPoint
                );


        http
                .securityMatcher("/v1/**")

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                apiAuthenticationEntryPoint
                        )
                )

                .authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated()
                )

                .addFilterBefore(
                        apiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain temporaryAdminSecurityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                );

        return http.build();
    }
}