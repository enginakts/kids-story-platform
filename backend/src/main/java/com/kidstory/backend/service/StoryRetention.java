package com.kidstory.backend.service;

import com.kidstory.backend.entity.Story;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Geçici (kaydedilmemiş) masalın ömrü (V16). app.stories.unsaved-ttl-hours (varsayılan 24) sonra masal okunamaz
 * ve UnsavedStoryCleaner tarafından dosyalarıyla silinir. Statik erişim, DTO'ların (StorySummaryResponse) bitiş
 * zamanını hesaplayabilmesi içindir; değer uygulama açılışında bir kez ayarlanır.
 */
@Component
public class StoryRetention {

    private static volatile Duration ttl = Duration.ofHours(24);

    public StoryRetention(@Value("${app.stories.unsaved-ttl-hours:24}") long hours) {
        ttl = Duration.ofHours(Math.max(1, hours));
    }

    public static Duration ttl() {
        return ttl;
    }

    /** Kaydedilmiş masal için null; geçici masal için silineceği an. */
    public static Instant expiresAt(Story story) {
        if (story.getSavedAt() != null || story.getCreatedAt() == null) {
            return null;
        }
        return story.getCreatedAt().plus(ttl);
    }

    public static boolean expired(Story story) {
        Instant at = expiresAt(story);
        return at != null && at.isBefore(Instant.now());
    }

    /** Bu andan önce oluşturulmuş geçici masalların süresi dolmuştur. */
    public static Instant cutoff() {
        return Instant.now().minus(ttl);
    }
}
