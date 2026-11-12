package com.knowledgeforge.knowledgeforge.linkpreview;

/** Bookmark metadata; every field except url may be null. */
public record LinkPreview(String url, String title, String description, String imageUrl) {}
