package com.vsm.api.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two modes:
 *
 * <ol>
 *   <li>Normal (auth enabled): JWT required for /api/**</li>
 *   <li>Local profile (spring.profiles.active=local): auth disabled for developer convenience</li>
 * </ol>
 */
@Configuration
public class SecurityConfig {

  @Bean
  @Profile("!local")
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            reg ->
                reg.requestMatchers(
                        "/health",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/api/coach/**")
                    .hasRole("COACH")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  @Profile("local")
  SecurityFilterChain localAllOpen(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(reg -> reg.anyRequest().permitAll());
    return http.build();
  }

  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    scopes.setAuthorityPrefix("SCOPE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> mergeAuthorities(jwt, scopes));
    return converter;
  }

  private Collection<GrantedAuthority> mergeAuthorities(
      Jwt jwt, JwtGrantedAuthoritiesConverter scopesConverter) {
    Collection<GrantedAuthority> authorities = new HashSet<>(scopesConverter.convert(jwt));
    List<String> groups = jwt.getClaimAsStringList("cognito:groups");
    if (groups != null) {
      groups.stream()
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
          .forEach(authorities::add);
    }
    return authorities;
  }
}
