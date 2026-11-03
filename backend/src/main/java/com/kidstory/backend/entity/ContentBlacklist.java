package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "content_blacklists")
public class ContentBlacklist extends CreatedEntity {

    public enum Action { BLOCK, REPLACE }

    @Column(nullable = false, unique = true, length = 100)
    private String keyword;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private Action actionType;

    @Column(name = "replacement_word", length = 100)
    private String replacementWord;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
