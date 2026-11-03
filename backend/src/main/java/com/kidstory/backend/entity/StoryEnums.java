package com.kidstory.backend.entity;

/** Masal ile ilgili sabit değer kümeleri. */
public final class StoryEnums {

    private StoryEnums() {
    }

    public enum Theme { FRIENDSHIP, HELPING, SHARING, NATURE_LOVE, EMOTIONS, COURAGE, SELF_CARE, DAILY_HABITS }

    public enum NarrationStyle { WARM, RHYMING, PLAYFUL, BEDTIME }

    public enum Mode { SINGLE_SHOT, INTERACTIVE }

    public enum Status { PENDING, GENERATING, COMPLETED, FAILED }

    public enum Visibility { PRIVATE, PUBLIC }

    public enum ModerationStatus { NOT_REQUESTED, PENDING, APPROVED, REJECTED }
}
