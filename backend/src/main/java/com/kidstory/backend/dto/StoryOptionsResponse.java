package com.kidstory.backend.dto;

import com.kidstory.backend.service.StoryCatalog;

import java.util.List;

public record StoryOptionsResponse(List<StoryCatalog.Choice> themes, List<StoryCatalog.Choice> narrationStyles,
                                   List<StoryCatalog.Choice> lengths) {

    public static StoryOptionsResponse create() {
        return new StoryOptionsResponse(StoryCatalog.themes(), StoryCatalog.styles(), StoryCatalog.lengths());
    }
}
