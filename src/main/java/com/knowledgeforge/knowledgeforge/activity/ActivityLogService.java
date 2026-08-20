package com.knowledgeforge.knowledgeforge.activity;

import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void log(String teamId, String userId, String action, String targetType, String targetId, String targetTitle) {
        ActivityLog a = new ActivityLog();
        a.setTeamId(teamId);
        a.setUserId(userId);
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setTargetTitle(targetTitle);
        a.setCreatedAt(new Date());
        activityLogRepository.save(a);
    }
}