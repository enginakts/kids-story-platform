package com.kidstory.backend.entity;

/** Masal ile ilgili sabit değer kümeleri. Etiketler sihirbazda ve prompt'ta kullanılır. */
public final class StoryEnums {

    private StoryEnums() {
    }

    public interface Labeled {
        String getLabel();
    }

    /** Masalın öğretmek istediği değer. */
    public enum Theme implements Labeled {
        FRIENDSHIP("Arkadaşlık"), HELPING("Yardımlaşma"), SHARING("Paylaşma"), NATURE_LOVE("Doğa sevgisi"),
        EMOTIONS("Duyguları tanıma"), COURAGE("Cesaret"), SELF_CARE("Kendine bakım"), DAILY_HABITS("Güzel alışkanlıklar");

        private final String label;

        Theme(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    /** Çocuğun yaşadığı ve masalın ele aldığı durum. */
    public enum Situation implements Labeled {
        FIRST_DAY_AT_SCHOOL("Okulda ilk gün"), NEW_SIBLING("Yeni kardeş"), BEDTIME("Uyku vakti"),
        BRUSHING_TEETH("Diş fırçalama"), SHARING_TOYS("Oyuncak paylaşma"), MAKING_FRIENDS("Yeni arkadaş edinme"),
        VISITING_DOCTOR("Doktora gitme"), TRYING_NEW_FOOD("Yeni yemek tatma"), TIDYING_UP("Odayı toplama"),
        WAITING_TURN("Sırasını bekleme");

        private final String label;

        Situation(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    /** Çocuğun tanıması istenen duygu. */
    public enum Emotion implements Labeled {
        JOY("Mutluluk"), EMPATHY("Empati"), PATIENCE("Sabır"), BRAVERY("Cesaret"), GRATITUDE("Minnettarlık"),
        CALM("Sakinlik"), CURIOSITY("Merak"), SELF_CONFIDENCE("Özgüven");

        private final String label;

        Emotion(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    public enum NarrationStyle implements Labeled {
        WARM("Sıcak"), RHYMING("Kafiyeli"), PLAYFUL("Eğlenceli"), BEDTIME("Uyku öncesi sakin");

        private final String label;

        NarrationStyle(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    public enum Mode { SINGLE_SHOT, INTERACTIVE }

    public enum Status { PENDING, GENERATING, COMPLETED, FAILED }

    public enum Visibility { PRIVATE, PUBLIC }

    public enum ModerationStatus { NOT_REQUESTED, PENDING, APPROVED, REJECTED }
}
