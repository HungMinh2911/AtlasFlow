package com.atlasflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingController.class)
class PingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsServiceStatus() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "service": "atlasflow-backend",
                          "status": "ok"
                        }
                        """));
    }

    @Test
    void requestIdShouldBePreservedInResponse() throws Exception {
        mockMvc.perform(get("/api/v1/ping")
                        .header("X-Request-Id", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Request-Id",
                        "test-request-123"
                ));
    }
}
