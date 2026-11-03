package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Final kapsamı: masal sonrası sohbet asistanı oturumu. */
@Getter @Setter
@Entity @Table(name = "chat_sessions")
public class ChatSession extends CreatedEntity {

    public enum Status { ACTIVE, ENDED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private ChildProfile child;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "ended_at")
    private Instant endedAt;
}
