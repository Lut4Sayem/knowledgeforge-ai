package com.knowledgeforge.knowledgeforge.search;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<SearchResultDTO>> search(@RequestParam(name = "q") String q) {
        return ResponseEntity.ok(searchService.search(q));
    }
}