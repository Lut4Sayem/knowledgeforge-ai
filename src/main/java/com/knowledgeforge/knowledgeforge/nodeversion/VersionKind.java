package com.knowledgeforge.knowledgeforge.nodeversion;

/** RECOVERED = a stale client's unsaved copy, kept privately instead of merged (D6 epoch rule). Never merged with other versions. */
public enum VersionKind { AUTO, NAMED, PUBLISHED, PUBLISHED_TO_TEAM, RESTORED, RECOVERED }
