package com.tlcn.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final RedisTemplate<String, String> redisTemplate;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
        RedisTemplate<String, String> redisTemplate,
        CorsConfigurationSource corsConfigurationSource
    ) {
        this.redisTemplate = redisTemplate;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/payments/vnpay-return").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/vnpay-ipn").permitAll()
                .requestMatchers(HttpMethod.POST, "/payments/internal/**").permitAll()
                .requestMatchers("/webhooks/settlement/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                .requestMatchers("/payments/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/payments/vendor/**").hasRole("VENDOR")
                .requestMatchers("/payments/customers/**").hasRole("CUSTOMER")

                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2
                    .jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new KeycloakConverter())
                    )
                    .authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                    )
            )

            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                )
            );

        http.addFilterBefore(
            new JwtBlacklistFilter(redisTemplate),
            BasicAuthenticationFilter.class
        );

        return http.build();
    }
}