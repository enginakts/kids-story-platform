package com.kidstory.backend.dto;

import com.kidstory.backend.entity.LocationBible;

import java.util.List;

/**
 * Ebeveynin gördüğü mekân bilgisi (Türkçe). Resim modeline giden İngilizce çevre bilgisi (tarif, ışık, sabit öğeler)
 * gösterilmez. images: yöneticinin yüklediği örnek resimlerin adresleri; ilki kapak olarak kullanılır.
 */
public record LocationResponse(Long id, String name, String environmentType, String atmosphereDescription,
                               List<String> images) {

    public static LocationResponse from(LocationBible l, List<String> images) {
        return new LocationResponse(l.getId(), l.getName(), l.getEnvironmentType(), l.getAtmosphereDescription(), images);
    }

    /** Kapak resmi: ilk örnek resim; yoksa null. */
    public String cover() {
        return images == null || images.isEmpty() ? null : images.getFirst();
    }
}
