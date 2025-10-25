package com.vsm.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class PayloadSizeFilter extends OncePerRequestFilter {
  private final SecurityProperties securityProperties;

  public PayloadSizeFilter(SecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long maxPayloadBytes = securityProperties.getMaxPayloadBytes();
    if (maxPayloadBytes > 0) {
      long contentLength = request.getContentLengthLong();
      if (contentLength > maxPayloadBytes) {
        response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Request payload too large");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
