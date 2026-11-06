package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/api/documents/{documentId}/ai/summary")
    public SummaryResponse summarize(@PathVariable String documentId) {
        return aiService.summarize(documentId);
    }
}
