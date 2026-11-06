package com.kidstory.backend.service.story;

import com.kidstory.backend.entity.ContentBlacklist;
import com.kidstory.backend.repository.ContentBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * content_blacklists kurallarını uygular. REPLACE kelimeyi değiştirir, BLOCK metni reddeder.
 * 4 harf ve üzeri kelimeler kök olarak eşleşir (öldür → öldürdü); daha kısalar yalnızca tam kelime
 * olarak eşleşir (kan → kanat eşleşmez).
 */
@Service
@RequiredArgsConstructor
public class ContentSafetyFilter {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final ContentBlacklistRepository blacklist;

    /** Metin engellenen bir kelime içerdiğinde atılır. */
    public static class UnsafeContentException extends RuntimeException {
        public UnsafeContentException(String category) {
            super("Güvenli olmayan içerik: " + category);
        }
    }

    public record Rules(List<ContentBlacklist> entries) {
    }

    @Transactional(readOnly = true)
    public Rules rules() {
        return new Rules(blacklist.findByActiveTrue());
    }

    /** REPLACE kurallarını uygulanmış metni döner; BLOCK kuralı tetiklenirse UnsafeContentException atar. */
    public String clean(String text, Rules rules) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (ContentBlacklist entry : rules.entries()) {
            Matcher m = pattern(entry.getKeyword()).matcher(result);
            if (!m.find()) {
                continue;
            }
            if (entry.getActionType() == ContentBlacklist.Action.BLOCK || entry.getReplacementWord() == null) {
                throw new UnsafeContentException(entry.getCategory());
            }
            result = m.replaceAll(Matcher.quoteReplacement(entry.getReplacementWord()));
        }
        return result;
    }

    /** Engellenen içerik varsa null döner; ebeveynin serbest metinleri prompt'a bu yolla eklenir. */
    public String cleanOrNull(String text, Rules rules) {
        try {
            return clean(text, rules);
        } catch (UnsafeContentException e) {
            return null;
        }
    }

    private static Pattern pattern(String keyword) {
        String word = Pattern.quote(keyword.toLowerCase(TR));
        String tail = keyword.length() >= 4 ? "" : "(?!\\p{L})";
        return Pattern.compile("(?<!\\p{L})" + word + tail, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
