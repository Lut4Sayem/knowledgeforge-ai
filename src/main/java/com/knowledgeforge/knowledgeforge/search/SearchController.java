package com.knowledgeforge.knowledgeforge.search;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<SearchResultDTO> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "publishState", required = false) String publishState,
            @RequestParam(name = "spaceId", required = false) String spaceId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return searchService.search(q, tag, publishState, spaceId, limit);
    }

    @GetMapping("/me/recent")
    public List<SearchResultDTO> recent() {
        return searchService.recent();
    }
}
