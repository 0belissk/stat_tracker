package com.vsm.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local") // disable auth in tests
class CoachReportsControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createReport_returns202_forValidPayload() throws Exception {
        String body = """
          {
            \"playerId\": \"p123\",
            \"playerEmail\": \"player@example.com\",
            \"categories\": { \"Aces\": \"5\", \"Blocks\": \"3\" }
          }
        """;
        mvc.perform(post("/api/coach/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isAccepted());
    }

    @Test
    void createReport_returns400_forInvalidEmail() throws Exception {
        String body = """
          {
            \"playerId\": \"p123\",
            \"playerEmail\": \"not-an-email\",
            \"categories\": { \"Aces\": \"5\" }
          }
        """;
        mvc.perform(post("/api/coach/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isBadRequest());
    }
}
