package com.knowledgeforge.knowledgeforge.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Daily limits and usage recording. Repositories are mocked; no database needed.
class AiServiceTest {

    AiUsageRepository usage = mock(AiUsageRepository.class);
    AiService service = new AiService(usage, 10, 1000);

    private static AiUsage usageWithTokens(int total) {
        AiUsage u = new AiUsage();
        u.setTotalTokens(total);
        return u;
    }

    @Test
    void underBothCapsPasses() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq("u1"), any(Date.class))).thenReturn(9L);
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class))).thenReturn(List.of(usageWithTokens(999)));
        assertDoesNotThrow(() -> service.checkLimits("u1"));
    }

    @Test
    void userCapRefusedWith429() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq("u1"), any(Date.class))).thenReturn(10L);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.checkLimits("u1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals("You've used today's 10 AI requests. Resets at 00:00 UTC.", e.getReason());
    }

    @Test
    void siteTokenCapRefusedWith429() {
        when(usage.countByUserIdAndCreatedAtGreaterThanEqual(eq("u1"), any(Date.class))).thenReturn(0L);
        when(usage.findByCreatedAtGreaterThanEqual(any(Date.class)))
                .thenReturn(List.of(usageWithTokens(600), usageWithTokens(400)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.checkLimits("u1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals("Today's AI limit for the site is used up. Try again tomorrow.", e.getReason());
    }

    @Test
    void recordUsageSavesNumbersOnly() {
        service.recordUsage("u1", "t1", "ASK", new AiClient.Result("answer text", 10, 5, 15));
        ArgumentCaptor<AiUsage> saved = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(saved.capture());
        AiUsage u = saved.getValue();
        assertEquals("u1", u.getUserId());
        assertEquals("t1", u.getTeamId());
        assertEquals("ASK", u.getFeature());
        assertEquals(15, u.getTotalTokens());
        assertNotNull(u.getCreatedAt());
    }
}
