package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.util.Date;
import java.util.List;

public record NodeDTO(String id, String spaceId, String parentId, String title, String icon, List<String> tags,
                      PublishState publishState, boolean locked, String lockedBy, NodeStatus status, Access access,
                      List<Crumb> breadcrumbs, UserRef createdBy, UserRef updatedBy, Date contentUpdatedAt) {
    public record Crumb(String id, String title) {}
}
