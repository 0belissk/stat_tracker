package com.vsm.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void generatesCorrelationIdWhenMissing() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    String headerValue = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(headerValue).isNotBlank();
    assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE))
        .isEqualTo(headerValue);
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void reusesProvidedCorrelationId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "existing-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .isEqualTo("existing-id");
    assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_REQUEST_ATTRIBUTE))
        .isEqualTo("existing-id");
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void includesTraceHeaderWhenPresent() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.XRAY_TRACE_HEADER, "Root=1-abc;Parent=def");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(MDC.get(CorrelationIdFilter.XRAY_TRACE_MDC_KEY)).isNull();
  }
}
