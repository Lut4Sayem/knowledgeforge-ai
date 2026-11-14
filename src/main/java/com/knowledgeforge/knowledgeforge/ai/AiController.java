package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/api/nodes/{nodeId}/ai/summary")
    public SummaryResponse summarize(@PathVariable String nodeId) {
        return aiService.summarize(nodeId);
    }

    @PostMapping("/api/spaces/{spaceId}/ai/ask")
    public AskResponse ask(@PathVariable String spaceId, @RequestBody AskRequest request) {
        return aiService.ask(spaceId, request == null ? null : request.question());
    }
}
