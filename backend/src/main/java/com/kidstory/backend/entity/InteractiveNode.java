package com.kidstory.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Entity @Table(name = "interactive_nodes")
public class InteractiveNode extends CreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id")
    private StoryScene scene;

    @Column(name = "node_step", nullable = false)
    private int nodeStep;

    @Column(name = "prompt_question", nullable = false)
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
    private String selectedChoice;
}
