package ua.lab3.registry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

@WebMvcTest(NodeController.class)
class NodeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NodeService service;

    private static String body(String warning, String anomaly) {
        return "{\"node_id\":\"node-1\",\"name\":\"API\",\"type\":\"SPRING_BOOT\","
                + "\"thresholds\":{\"warning\":" + warning + ",\"anomaly\":" + anomaly + "}}";
    }

    private void expectProblem(String json, HttpStatus status) throws Exception {
        mvc.perform(post("/v1/nodes").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidThresholdsAre400() throws Exception {
        expectProblem(body("0.9", "0.5"), HttpStatus.BAD_REQUEST);
        expectProblem(body("0.5", "1.5"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownTypeIs400() throws Exception {
        expectProblem(body("0.5", "0.8").replace("SPRING_BOOT", "TOASTER"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void existingNodeIs409() throws Exception {
        when(service.create(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "exists"));
        expectProblem(body("0.5", "0.8"), HttpStatus.CONFLICT);
    }

    @Test
    void nodeWithoutMetricsIs422() throws Exception {
        when(service.create(any())).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "no metrics"));
        expectProblem(body("0.5", "0.8"), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
