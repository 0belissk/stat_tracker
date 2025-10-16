package com.vsm.api;

import com.vsm.api.config.TestAwsClientsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("local") // disable auth for test boot
@ContextConfiguration(classes = TestAwsClientsConfig.class)
class PlayersApiApplicationTests {

  @Test
  void contextLoads() {
    // if the app context fails to start, this test fails
  }
}
