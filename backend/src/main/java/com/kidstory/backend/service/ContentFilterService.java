package com.kidstory.backend.service;

import com.kidstory.backend.entity.ContentBlacklist;
import com.kidstory.backend.repository.ContentBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ebeveynin serbest metnini kara listeye göre denetler (Adım 9'da LLM çıktısı için de kullanılacak).
 * Uzun kelimeler kelime başında eşleşir (Türkçe ekler yakalanır: "canavarlar");
 * 4 harften kısa kelimeler yalnızca tam kelime olarak eşleşir ("kan" → "kanat" engellenmez).
 */
@Service
@RequiredArgsConstructor
public class ContentFilterService {

    static final Locale TR = Locale.forLanguageTag("tr");

    private final ContentBlacklistRepository blacklist;

    @Transactional(readOnly = true)
    public Optional<String> findBlockedWord(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        return blacklist.findByActiveTrue().stream()
                .map(ContentBlacklist::getKeyword)
                .filter(keyword -> pattern(normalize(keyword)).matcher(normalized).find())
                .findFirst();
    }

    static String normalize(String text) {
        return text.toLowerCase(TR).trim();
    }

    /** Metinde verilen ifadenin tam kelime olarak geçip geçmediği (büyük/küçük harf ve Türkçe İ/ı duyarsız). */
    static boolean containsWord(String text, String word) {
        String w = normalize(word);
        return !w.isEmpty()
                && Pattern.compile("(?<!\\p{L})" + Pattern.quote(w) + "(?!\\p{L})").matcher(normalize(text)).find();
    }

    private static Pattern pattern(String keyword) {
        String end = keyword.codePointCount(0, keyword.length()) < 4 ? "(?!\\p{L})" : "";
        return Pattern.compile("(?<!\\p{L})" + Pattern.quote(keyword) + end);
    }
}
