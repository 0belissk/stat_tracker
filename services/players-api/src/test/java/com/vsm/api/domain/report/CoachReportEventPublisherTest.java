package com.vsm.api.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

class CoachReportEventPublisherTest {

  private final EventBridgeClient eventBridgeClient = Mockito.mock(EventBridgeClient.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock = Clock.fixed(Instant.parse("2024-04-01T12:00:00Z"), ZoneOffset.UTC);
  private final CoachReportEventPublisher publisher =
      new CoachReportEventPublisher(
          eventBridgeClient, objectMapper, clock, "vsm-events", "players-api.reports");

  @Test
  void publishReportCreatedSendsEvent() {
    Mockito.when(eventBridgeClient.putEvents(Mockito.any(PutEventsRequest.class)))
        .thenReturn(PutEventsResponse.builder().failedEntryCount(0).build());

    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-03-01T00:00:00Z"),
            "2024-03-01T00:00:00Z",
            "coach-1");

    publisher.publishReportCreated(report, "reports/key.txt");

    ArgumentCaptor<PutEventsRequest> captor = ArgumentCaptor.forClass(PutEventsRequest.class);
    verify(eventBridgeClient).putEvents(captor.capture());

    PutEventsRequest request = captor.getValue();
    assertEquals(1, request.entries().size());
    assertEquals("vsm-events", request.entries().get(0).eventBusName());
    assertEquals("report.created", request.entries().get(0).detailType());
    assertEquals("players-api.reports", request.entries().get(0).source());
    assertEquals(Instant.parse("2024-04-01T12:00:00Z"), request.entries().get(0).time());
    assertEquals(
        "{\"playerId\":\"player-1\",\"reportId\":\"2024-03-01T00:00:00Z\",\"s3Key\":\"reports/key.txt\"}",
        request.entries().get(0).detail());
  }

  @Test
  void publishReportCreatedThrowsWhenFailures() {
    Mockito.when(eventBridgeClient.putEvents(Mockito.any(PutEventsRequest.class)))
        .thenReturn(PutEventsResponse.builder().failedEntryCount(1).build());

    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-03-01T00:00:00Z"),
            "2024-03-01T00:00:00Z",
            "coach-1");

    assertThrows(
        IllegalStateException.class, () -> publisher.publishReportCreated(report, "reports/key.txt"));
  }

  @Test
  void publishReportCreatedWrapsSerializationErrors() throws JsonProcessingException {
    ObjectMapper failingMapper = Mockito.mock(ObjectMapper.class);
    Mockito.when(failingMapper.writeValueAsString(Mockito.any()))
        .thenThrow(new JsonProcessingException("boom") {});

    CoachReportEventPublisher failingPublisher =
        new CoachReportEventPublisher(
            eventBridgeClient, failingMapper, clock, "vsm-events", "players-api.reports");

    CoachReport report =
        new CoachReport(
            "player-1",
            "player@example.com",
            Map.of("serving", "great"),
            Instant.parse("2024-03-01T00:00:00Z"),
            "2024-03-01T00:00:00Z",
            "coach-1");

    assertThrows(
        IllegalStateException.class, () -> failingPublisher.publishReportCreated(report, "reports/key.txt"));
  }
}
