package com.knowledgeforge.knowledgeforge.ai;

import java.util.Date;

public record SummaryResponse(String summary, boolean cached, boolean trimmed, Date generatedAt) {}
