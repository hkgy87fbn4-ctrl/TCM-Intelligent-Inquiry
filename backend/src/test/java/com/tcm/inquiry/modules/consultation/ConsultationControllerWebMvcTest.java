package com.tcm.inquiry.modules.consultation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tcm.inquiry.modules.consultation.dto.ChatSessionResponse;

@WebMvcTest(ConsultationController.class)
class ConsultationControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ConsultationService consultationService;
    @MockBean private ConsultationChatService consultationChatService;

    @Test
    void healthOk() throws Exception {
        mockMvc.perform(get("/api/v1/consultation/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("consultation"));
    }

    @Test
    void listSessions() throws Exception {
        org.mockito.Mockito.when(consultationService.listSessions())
                .thenReturn(List.of(new ChatSessionResponse(1L, "s", null, null)));

        mockMvc.perform(get("/api/v1/consultation/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }
}
