package com.knowledgeforge.knowledgeforge.ai;

import java.util.List;

public record AskResponse(String answer, List<SourceDTO> sources, int usedDocuments, boolean trimmed) {}
