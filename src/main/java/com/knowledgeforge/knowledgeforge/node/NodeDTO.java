package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.access.Access;
import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.util.Date;
import java.util.List;

/** {@code canManage}: the user is a page editor even if the page is locked or archived (lock, archive, trash menu). */
public record NodeDTO(String id, String spaceId, String parentId, String title, String icon, List<String> tags,
                      PublishState publishState, boolean locked, String lockedBy, NodeStatus status, Access access,
                      boolean canManage,
                      List<Crumb> breadcrumbs, UserRef createdBy, UserRef updatedBy, Date contentUpdatedAt) {
    public record Crumb(String id, String title) {}
}
