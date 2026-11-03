package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Masalın yan karakteri. Ana karakter stories.primary_character_id alanındadır. */
@Getter @Setter
@Entity @Table(name = "story_characters")
public class StoryCharacter extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private CharacterBible character;
}
