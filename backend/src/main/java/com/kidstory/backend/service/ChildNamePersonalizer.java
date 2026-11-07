package com.kidstory.backend.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Çocuğun adı LLM'e gönderilmez. Model çocuk için uydurma bir yer tutucu ad kullanır; burada gerçek adla değiştirilir.
 * Kesme işaretinden sonraki hal eki, gerçek adın ünlü uyumuna göre yeniden çekimlenir:
 * "Kiko'nun" → "Ada'nın", "Kiko'ya" → "Can'a", "Kiko'da" → "Burak'ta".
 */
public final class ChildNamePersonalizer {

    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final String BACK = "aıou";
    private static final String FRONT = "eiöü";
    private static final String ROUND = "ouöü";
    private static final String HARD = "çfhkpsşt";

    private ChildNamePersonalizer() {
    }

    public static String apply(String text, String placeholder, String name) {
        Pattern p = Pattern.compile("(?<!\\p{L})" + Pattern.quote(placeholder) + "(?:(['’])(\\p{L}+))?(?!\\p{L})");
        Matcher m = p.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(2) == null ? name : name + "'" + inflect(name, m.group(2));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static boolean mentions(String text, String placeholder) {
        return Pattern.compile("(?<!\\p{L})" + Pattern.quote(placeholder) + "(?!\\p{L})").matcher(text).find();
    }

    /** Ek, biçimine göre sınıflanır ve gerçek ada göre yeniden kurulur. Tanınmayan ek olduğu gibi bırakılır. */
    static String inflect(String name, String suffix) {
        String s = suffix.toLowerCase(TR);
        String word = name.trim().toLowerCase(TR);
        word = word.substring(word.lastIndexOf(' ') + 1);
        char last = word.isEmpty() ? 'e' : word.charAt(word.length() - 1);
        boolean endsVowel = (BACK + FRONT).indexOf(last) >= 0;
        boolean hard = HARD.indexOf(last) >= 0;
        char vowel = lastVowel(word);
        boolean back = BACK.indexOf(vowel) >= 0;
        boolean round = ROUND.indexOf(vowel) >= 0;
        String i4 = back ? (round ? "u" : "ı") : (round ? "ü" : "i");
        String a2 = back ? "a" : "e";
        String d = hard ? "t" : "d";

        if (s.matches("n?[ıiuü]n")) {
            return endsVowel ? "n" + i4 + "n" : i4 + "n";
        }
        if (s.matches("[dt][ae]n")) {
            return d + a2 + "n";
        }
        if (s.matches("[dt][ae]")) {
            return d + a2;
        }
        if (s.matches("y?l[ae]")) {
            return endsVowel ? "yl" + a2 : "l" + a2;
        }
        if (s.matches("y?[ae]")) {
            return endsVowel ? "y" + a2 : a2;
        }
        if (s.matches("y?[ıiuü]")) {
            return endsVowel ? "y" + i4 : i4;
        }
        return suffix;
    }

    private static char lastVowel(String word) {
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if ((BACK + FRONT).indexOf(c) >= 0) {
                return c;
            }
        }
        return 'e';
    }
}
