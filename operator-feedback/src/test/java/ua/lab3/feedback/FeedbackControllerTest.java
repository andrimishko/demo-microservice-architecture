package ua.lab3.feedback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(FeedbackController.class)
class FeedbackControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean FeedbackService service;

    private void expectProblem(String path, String json, HttpStatus status) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidBodiesAre400() throws Exception {
        expectProblem("/v1/feedback", "{\"verdict\":\"CONFIRMED\"}", HttpStatus.BAD_REQUEST);                          // no analysis_id
        expectProblem("/v1/feedback", "{\"analysis_id\":\"a\",\"verdict\":\"MAYBE\"}", HttpStatus.BAD_REQUEST);        // unknown verdict
        expectProblem("/v1/feedback", "{\"analysis_id\":\"a\",\"verdict\":\"FALSE_POSITIVE\",\"cause\":\"RAIN\"}", HttpStatus.BAD_REQUEST);
        expectProblem("/v1/feedback/missed", "{\"node_id\":\"node-1\"}", HttpStatus.BAD_REQUEST);                      // no from/to
        expectProblem("/v1/suggestions", "{\"text\":\"\"}", HttpStatus.BAD_REQUEST);
    }

    @Test
    void serviceErrorsKeepTheirStatus() throws Exception {
        String body = "{\"analysis_id\":\"a\",\"verdict\":\"CONFIRMED\"}";
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no snapshot")).when(service).submit(any(), any());
        expectProblem("/v1/feedback", body, HttpStatus.NOT_FOUND);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "exists")).when(service).submit(any(), any());
        expectProblem("/v1/feedback", body, HttpStatus.CONFLICT);
    }
}
