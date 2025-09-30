package com.vsm.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local") // disable auth for test boot
class PlayersApiApplicationTests {

    @Test
    void contextLoads() {
        // if the app context fails to start, this test fails
    }
}
