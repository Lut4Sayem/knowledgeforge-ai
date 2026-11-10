package com.knowledgeforge.knowledgeforge.node;

import com.knowledgeforge.knowledgeforge.common.UserRef;

import java.util.Date;
import java.util.List;

/**
 * One trashed or archived branch (its root). {@code purgeAt} is null for archived branches.
 * {@code canRestore}: the caller may restore / unarchive it (edit rights on the branch root).
 * {@code parentGone}: the parent no longer exists or is not active, so a restore lands at the top level.
 */
public record BranchDTO(String id, String title, String icon, List<NodeDTO.Crumb> path, int descendantCount,
                        UserRef by, Date at, Date purgeAt, boolean parentGone, boolean canRestore) {
}
