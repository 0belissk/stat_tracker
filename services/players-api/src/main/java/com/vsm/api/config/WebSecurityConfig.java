package com.vsm.api.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, SecurityProperties.class})
public class WebSecurityConfig {
  private final CorsProperties corsProperties;

  public WebSecurityConfig(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (allowedOrigins.isEmpty()) {
          return;
        }

        registry
            .addMapping("/api/**")
            .allowedOrigins(allowedOrigins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
      }
    };
  }

  @Bean
  public FilterRegistrationBean<PayloadSizeFilter> payloadSizeFilter(SecurityProperties securityProperties) {
    FilterRegistrationBean<PayloadSizeFilter> registration =
        new FilterRegistrationBean<>(new PayloadSizeFilter(securityProperties));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
