package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interactive_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractiveNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private StoryScene scene;

    @Column(name = "node_step", nullable = false)
    private Integer nodeStep;

    @Column(name = "prompt_question", nullable = false, length = 255)
    private String promptQuestion;

    @Column(name = "choice_a_text", nullable = false, length = 100)
    private String choiceAText;

    @Column(name = "choice_a_icon", nullable = false, length = 100)
    private String choiceAIcon;

    @Column(name = "choice_b_text", nullable = false, length = 100)
    private String choiceBText;

    @Column(name = "choice_b_icon", nullable = false, length = 100)
    private String choiceBIcon;

    @Column(name = "selected_choice", length = 10)
    private String selectedChoice; // 'A' veya 'B'

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
