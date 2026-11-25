package ua.lab3.feedback;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;
import ua.lab3.feedback.Dtos.FeedbackView;
import ua.lab3.feedback.Dtos.MissedRequest;
import ua.lab3.feedback.Dtos.MissedResult;
import ua.lab3.feedback.Dtos.Stats;
import ua.lab3.feedback.Dtos.SuggestionRequest;

@RestController
@RequestMapping("/v1")
public class FeedbackController {
    // shortcut: the author is whatever the caller puts in X-User; upgrade: take it from the gateway's verified identity (stage 6).
    private static final String USER = "X-User";

    private final FeedbackService service;

    FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    FeedbackView submit(@Valid @RequestBody FeedbackRequest req, @RequestHeader(value = USER, defaultValue = "anonymous") String user) {
        return FeedbackView.of(service.submit(req, user));
    }

    @PostMapping("/feedback/missed")
    @ResponseStatus(HttpStatus.CREATED)
    MissedResult missed(@Valid @RequestBody MissedRequest req, @RequestHeader(value = USER, defaultValue = "anonymous") String user) {
        return service.markMissed(req, user);
    }

    @GetMapping("/feedback")
    List<FeedbackView> list(@RequestParam(name = "node_id", required = false) String nodeId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                            @RequestParam(required = false) Verdict verdict) {
        return service.list(nodeId, from, to, verdict).stream().map(FeedbackView::of).toList();
    }

    @GetMapping("/feedback/stats")
    Stats stats(@RequestParam("node_id") String nodeId, @RequestParam(defaultValue = "PT1H") String period) {
        return service.stats(nodeId, period);
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> suggest(@Valid @RequestBody SuggestionRequest req, @RequestHeader(value = USER, defaultValue = "anonymous") String user) {
        SuggestionEntity s = service.suggest(req.text(), user);
        return Map.of("id", s.id(), "text", s.text());
    }
}
