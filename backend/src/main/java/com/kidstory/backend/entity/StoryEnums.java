package com.kidstory.backend.entity;

/** Masal ile ilgili sabit değer kümeleri. Türkçe etiketler StoryCatalog'dadır. */
public final class StoryEnums {

    private StoryEnums() {
    }

    public enum Theme { FRIENDSHIP, HELPING, SHARING, NATURE_LOVE, EMOTIONS, COURAGE, SELF_CARE, DAILY_HABITS }

    public enum NarrationStyle { WARM, RHYMING, PLAYFUL, BEDTIME }

    /** Masal uzunluğu: sahne sayısını ve hedef süreyi belirler. */
    public enum Length {
        SHORT(4, 2), MEDIUM(5, 3), LONG(6, 4);

        private final int scenes;
        private final int minutes;

        Length(int scenes, int minutes) {
            this.scenes = scenes;
            this.minutes = minutes;
        }

        public int scenes() {
            return scenes;
        }

        public int minutes() {
            return minutes;
        }
    }

    public enum Mode { SINGLE_SHOT, INTERACTIVE }

    public enum Status { PENDING, GENERATING, COMPLETED, FAILED }

    public enum Visibility { PRIVATE, PUBLIC }

    public enum ModerationStatus { NOT_REQUESTED, PENDING, APPROVED, REJECTED }
}
