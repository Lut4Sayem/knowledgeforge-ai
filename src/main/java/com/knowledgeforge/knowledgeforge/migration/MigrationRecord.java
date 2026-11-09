package com.knowledgeforge.knowledgeforge.migration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

/** One row per migration step (physical schema 3.14). _id is the step name. */
@Getter @Setter
@Document(collection = "migrations")
public class MigrationRecord {
    public enum Status { RUNNING, DONE, FAILED }

    @Id
    private String id;
    private Date startedAt;
    private Date finishedAt;
    private Status status;
    private Map<String, Long> counts;
}
