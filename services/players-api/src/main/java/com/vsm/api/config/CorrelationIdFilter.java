package com.vsm.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a correlation identifier for tracing across systems.
 *
 * <p>If the caller supplies {@value #CORRELATION_ID_HEADER} we reuse it, otherwise we generate a
 * new UUID. The value is added to the logging MDC so log entries include the same identifier. When
 * AWS X-Ray trace headers are present we also expose them in the MDC for structured logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String XRAY_TRACE_HEADER = "X-Amzn-Trace-Id";
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";
  public static final String XRAY_TRACE_MDC_KEY = "AWS-XRAY-TRACE-ID";
  public static final String CORRELATION_ID_REQUEST_ATTRIBUTE = "correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = resolveCorrelationId(request);
    String traceHeader = request.getHeader(XRAY_TRACE_HEADER);

    try {
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
      if (StringUtils.hasText(traceHeader)) {
        MDC.put(XRAY_TRACE_MDC_KEY, traceHeader);
      }

      request.setAttribute(CORRELATION_ID_REQUEST_ATTRIBUTE, correlationId);
      response.setHeader(CORRELATION_ID_HEADER, correlationId);

      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(CORRELATION_ID_MDC_KEY);
      MDC.remove(XRAY_TRACE_MDC_KEY);
    }
  }

  private String resolveCorrelationId(HttpServletRequest request) {
    String headerValue = request.getHeader(CORRELATION_ID_HEADER);
    if (StringUtils.hasText(headerValue)) {
      return headerValue.trim();
    }
    return UUID.randomUUID().toString();
  }
}
