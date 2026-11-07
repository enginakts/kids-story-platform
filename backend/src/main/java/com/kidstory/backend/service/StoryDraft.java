package com.kidstory.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** Sahnelere bölünmüş masal taslağı ve beklenen JSON şeması. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoryDraft(String title, List<Scene> scenes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scene(String text, @JsonProperty("image_prompt") String imagePrompt) {
    }

    public static final String SCHEMA_NAME = "story";

    /** Groq strict modu için: tüm alanlar zorunlu, ek alan yok. */
    public static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("title", "scenes"),
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "scenes", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "additionalProperties", false,
                                    "required", List.of("text", "image_prompt"),
                                    "properties", Map.of(
                                            "text", Map.of("type", "string"),
                                            "image_prompt", Map.of("type", "string"))))));

    /** Gemini için: yalnızca temel JSON şeması anahtar kelimeleri. */
    public static final Map<String, Object> SCENE_SCHEMA = Map.of(
            "type", "object",
            "required", List.of("title", "scenes"),
            "properties", Map.of(
                    "title", Map.of("type", "string"),
                    "scenes", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "required", List.of("text", "image_prompt"),
                                    "properties", Map.of(
                                            "text", Map.of("type", "string"),
                                            "image_prompt", Map.of("type", "string"))))));
}
