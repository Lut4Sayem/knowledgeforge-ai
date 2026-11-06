package com.knowledgeforge.knowledgeforge.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

// Step 2: daily limits and usage recording. Summarize and Ask flows are added in the next steps.
@Service
public class AiService {

    private final AiUsageRepository usageRepository;
    private final int userDailyLimit;
    private final long siteDailyTokens;

    public AiService(AiUsageRepository usageRepository,
                     @Value("${ai.user-daily-limit:10}") int userDailyLimit,
                     @Value("${ai.site-daily-tokens:180000}") long siteDailyTokens) {
        this.usageRepository = usageRepository;
        this.userDailyLimit = userDailyLimit;
        this.siteDailyTokens = siteDailyTokens;
    }

    // Throws 429 when this user or the whole site has used up today (UTC day).
    void checkLimits(String userId) {
        Date dayStart = Date.from(LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
        if (usageRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, dayStart) >= userDailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used today's " + userDailyLimit + " AI requests. Resets at 00:00 UTC.");
        }
        long tokensToday = usageRepository.findByCreatedAtGreaterThanEqual(dayStart).stream()
                .mapToLong(AiUsage::getTotalTokens).sum();
        if (tokensToday >= siteDailyTokens) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Today's AI limit for the site is used up. Try again tomorrow.");
        }
    }

    void recordUsage(String userId, String teamId, String feature, AiClient.Result r) {
        AiUsage u = new AiUsage();
        u.setUserId(userId);
        u.setTeamId(teamId);
        u.setFeature(feature);
        u.setPromptTokens(r.promptTokens());
        u.setCompletionTokens(r.completionTokens());
        u.setTotalTokens(r.totalTokens());
        u.setCreatedAt(new Date());
        usageRepository.save(u);
    }
}
