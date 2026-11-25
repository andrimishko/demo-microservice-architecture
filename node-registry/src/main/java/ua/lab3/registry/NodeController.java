package ua.lab3.registry;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.lab3.registry.Dtos.CreateNode;
import ua.lab3.registry.Dtos.NodeView;
import ua.lab3.registry.Dtos.PatchNode;

@RestController
@RequestMapping("/v1")
public class NodeController {
    private final NodeService service;

    NodeController(NodeService service) {
        this.service = service;
    }

    @PostMapping("/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    NodeView create(@Valid @RequestBody CreateNode req) {
        return NodeView.of(service.create(req));
    }

    @GetMapping("/nodes")
    List<NodeView> list(@RequestParam(required = false) Boolean enabled) {
        return service.list(enabled).stream().map(NodeView::of).toList();
    }

    @GetMapping("/nodes/{id}")
    NodeView get(@PathVariable String id) {
        return NodeView.of(service.get(id));
    }

    @PatchMapping("/nodes/{id}")
    NodeView patch(@PathVariable String id, @Valid @RequestBody PatchNode req) {
        return NodeView.of(service.patch(id, req));
    }

    @DeleteMapping("/nodes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(id);
    }

    @GetMapping("/nodes/{id}/queries")
    Map<String, String> queries(@PathVariable String id) {
        return service.queries(id);
    }

    @GetMapping("/node-types")
    List<Map<String, Object>> types() {
        return Arrays.stream(NodeType.values()).map(t -> Map.<String, Object>of("type", t.name(), "metrics", t.metrics())).toList();
    }
}
