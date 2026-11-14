package com.kidstory.backend.service;

import com.kidstory.backend.dto.CharacterRequest;
import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.dto.LocationForm;
import com.kidstory.backend.dto.LocationResponse;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.CharacterBibleRepository;
import com.kidstory.backend.repository.LocationBibleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Karakter ve mekân kütüphanesi. Kayıtları yalnızca yöneticiler oluşturur ve değiştirir (yönetim paneli);
 * yayındaki kayıtlar bütün ebeveynlere görünür, ebeveynler yalnızca masal oluştururken seçer.
 */
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterBibleRepository characters;
    private final LocationBibleRepository locations;
    private final ContentFilterService contentFilter;
    private final AdminService admin;
    private final JdbcTemplate jdbc;
    private final LibraryImageService images;

    /** Yönetim listesinde bir karakter: kaç masalda kullanıldığı ve referans portresinin olup olmadığı. */
    public record CharacterRow(CharacterResponse character, boolean active, long stories, boolean portrait) {}

    /** Düzenleme sayfasındaki bir örnek resim: kaldırma kutusu için kimlik ve adres. */
    public record ImageRef(long id, String url) {}

    /** Yönetim listesinde bir mekân; resim tarifi alanları yalnızca yöneticiye gösterilir. */
    public record LocationRow(long id, String name, String environmentType, String atmosphereDescription,
                              String visualAnchorTokens, String lightingStyle, String recurringLandmarks,
                              boolean active, long stories, List<String> images) {

        public String cover() {
            return images.isEmpty() ? null : images.getFirst();
        }
    }

    // ---------- Ebeveyn (yalnızca okuma) ----------

    @Transactional(readOnly = true)
    public List<CharacterResponse> presets() {
        List<CharacterBible> list = characters.findBySystemPresetTrueAndActiveTrueOrderByIdAsc();
        Map<Long, List<Long>> pics = images.ids(LibraryImageService.Kind.CHARACTER, list.stream().map(CharacterBible::getId).toList());
        return list.stream().map(c -> CharacterResponse.from(c, LibraryImageService.urls(pics.get(c.getId())))).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> locations() {
        List<LocationBible> list = locations.findBySystemPresetTrueAndActiveTrueOrderByIdAsc();
        Map<Long, List<Long>> pics = images.ids(LibraryImageService.Kind.LOCATION, list.stream().map(LocationBible::getId).toList());
        return list.stream().map(l -> LocationResponse.from(l, LibraryImageService.urls(pics.get(l.getId())))).toList();
    }

    // ---------- Yönetim: karakterler ----------

    @Transactional(readOnly = true)
    public List<CharacterRow> allCharacters() {
        Map<Long, Long> usage = usage("SELECT character_id, count(DISTINCT story_id) FROM ("
                + " SELECT id AS story_id, primary_character_id AS character_id FROM stories WHERE primary_character_id IS NOT NULL"
                + " UNION ALL SELECT story_id, character_id FROM story_characters) u GROUP BY character_id");
        List<CharacterBible> list = characters.findBySystemPresetTrueOrderByActiveDescIdAsc();
        Map<Long, List<Long>> pics = images.ids(LibraryImageService.Kind.CHARACTER, list.stream().map(CharacterBible::getId).toList());
        return list.stream()
                .map(c -> new CharacterRow(CharacterResponse.from(c, LibraryImageService.urls(pics.get(c.getId()))), c.isActive(),
                        usage.getOrDefault(c.getId(), 0L), c.getReferenceImageUrl() != null))
                .toList();
    }

    @Transactional(readOnly = true)
    public CharacterResponse character(long id) {
        return CharacterResponse.from(systemCharacter(id), LibraryImageService.urls(images.ids(LibraryImageService.Kind.CHARACTER, id)));
    }

    @Transactional(readOnly = true)
    public List<ImageRef> characterImages(long id) {
        systemCharacter(id);
        return refs(images.ids(LibraryImageService.Kind.CHARACTER, id));
    }

    @Transactional
    public CharacterResponse createCharacter(long adminId, CharacterRequest request, LibraryImageService.Prepared pictures, String ip) {
        CharacterBible c = new CharacterBible();
        c.setSystemPreset(true);
        apply(c, request);
        if (pictures.isEmpty()) {
            throw new FieldValidationException("images", "En az bir örnek resim yükleyin; ebeveynler karakteri bu resimle görür");
        }
        characters.save(c);
        images.update(LibraryImageService.Kind.CHARACTER, c.getId(), pictures, List.of(), true);
        admin.audit(adminId, "CHARACTER_CREATED", "character", c.getId(),
                c.getName() + " (" + c.getSpecies() + ", " + pictures.images().size() + " örnek resim)", ip);
        return CharacterResponse.from(c);
    }

    @Transactional
    public CharacterResponse updateCharacter(long adminId, long id, CharacterRequest request, LibraryImageService.Prepared added,
                                             Collection<Long> removeImageIds, String ip) {
        CharacterBible c = systemCharacter(id);
        String before = c.getVisualAnchorTokens();
        apply(c, request);
        boolean hadImages = !images.ids(LibraryImageService.Kind.CHARACTER, id).isEmpty();
        images.update(LibraryImageService.Kind.CHARACTER, id, added, removeImageIds, hadImages);
        // Görünüm değiştiyse eski referans portresi artık karakteri anlatmaz: bir sonraki resimde yenisi çizilir.
        if (!Objects.equals(before, c.getVisualAnchorTokens())) {
            c.setReferenceImageUrl(null);
            c.setBaseSeed(null);
        }
        admin.audit(adminId, "CHARACTER_UPDATED", "character", id, c.getName(), ip);
        return CharacterResponse.from(c);
    }

    /** Masallarda kullanılmış olabileceği için silinmez; yayından kaldırılır ya da yeniden yayına alınır. */
    @Transactional
    public void setCharacterActive(long adminId, long id, boolean active, String ip) {
        CharacterBible c = systemCharacter(id);
        c.setActive(active);
        admin.audit(adminId, active ? "CHARACTER_ACTIVATED" : "CHARACTER_DEACTIVATED", "character", id, c.getName(), ip);
    }

    private CharacterBible systemCharacter(long id) {
        return characters.findByIdAndSystemPresetTrue(id).orElseThrow(() -> new NotFoundException("Karakter bulunamadı"));
    }

    private void apply(CharacterBible c, CharacterRequest r) {
        String name = r.name().trim();
        String personality = r.personalityTraits() == null || r.personalityTraits().isBlank() ? null : r.personalityTraits().trim();
        rejectBlocked("name", name);
        rejectBlocked("personalityTraits", personality);
        if (characters.existsBySystemPresetTrueAndNameIgnoreCaseAndIdNot(name, c.getId() == null ? -1L : c.getId())) {
            throw new FieldValidationException("name", "Bu adla bir karakter zaten var");
        }
        var species = CharacterCatalog.parse(CharacterCatalog.Species.class, r.speciesCode(), "speciesCode", null);
        var color = CharacterCatalog.parse(CharacterCatalog.Color.class, r.colorCode(), "colorCode", null);
        var outfit = CharacterCatalog.parse(CharacterCatalog.Outfit.class, r.outfitCode(), "outfitCode", CharacterCatalog.Outfit.YOK);
        var accessory = CharacterCatalog.parse(CharacterCatalog.Accessory.class, r.accessoryCode(), "accessoryCode",
                CharacterCatalog.Accessory.YOK);
        c.setName(name);
        c.setPersonalityTraits(personality);
        c.setSpecies(species.label());
        c.setSpeciesCode(species.name());
        c.setColorCode(color.name());
        c.setOutfitCode(outfit.name());
        c.setAccessoryCode(accessory.name());
        c.setClothingDescription(outfit == CharacterCatalog.Outfit.YOK ? null : outfit.label());
        c.setVisualAnchorTokens(CharacterCatalog.visualAnchor(species, color, outfit, accessory));
    }

    // ---------- Yönetim: mekânlar ----------

    @Transactional(readOnly = true)
    public List<LocationRow> allLocations() {
        Map<Long, Long> usage = usage("SELECT primary_location_id, count(*) FROM stories WHERE primary_location_id IS NOT NULL"
                + " GROUP BY primary_location_id");
        List<LocationBible> list = locations.findBySystemPresetTrueOrderByActiveDescIdAsc();
        Map<Long, List<Long>> pics = images.ids(LibraryImageService.Kind.LOCATION, list.stream().map(LocationBible::getId).toList());
        return list.stream()
                .map(l -> new LocationRow(l.getId(), l.getName(), l.getEnvironmentType(), l.getAtmosphereDescription(),
                        l.getVisualAnchorTokens(), l.getLightingStyle(), l.getRecurringLandmarks(), l.isActive(),
                        usage.getOrDefault(l.getId(), 0L), LibraryImageService.urls(pics.get(l.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationForm locationForm(long id) {
        LocationBible l = systemLocation(id);
        return new LocationForm(l.getName(), l.getEnvironmentType(), l.getAtmosphereDescription(), l.getVisualAnchorTokens(),
                l.getLightingStyle(), l.getRecurringLandmarks() == null ? "" : l.getRecurringLandmarks());
    }

    @Transactional(readOnly = true)
    public String locationName(long id) {
        return systemLocation(id).getName();
    }

    @Transactional(readOnly = true)
    public List<ImageRef> locationImages(long id) {
        systemLocation(id);
        return refs(images.ids(LibraryImageService.Kind.LOCATION, id));
    }

    @Transactional
    public long createLocation(long adminId, LocationForm form, LibraryImageService.Prepared pictures, String ip) {
        LocationBible l = new LocationBible();
        l.setSystemPreset(true);
        apply(l, form);
        if (pictures.isEmpty()) {
            throw new FieldValidationException("images", "En az bir örnek resim yükleyin; ebeveynler mekânı bu resimle görür");
        }
        locations.save(l);
        images.update(LibraryImageService.Kind.LOCATION, l.getId(), pictures, List.of(), true);
        admin.audit(adminId, "LOCATION_CREATED", "location", l.getId(), l.getName() + " (" + pictures.images().size() + " örnek resim)", ip);
        return l.getId();
    }

    @Transactional
    public void updateLocation(long adminId, long id, LocationForm form, LibraryImageService.Prepared added,
                               Collection<Long> removeImageIds, String ip) {
        LocationBible l = systemLocation(id);
        apply(l, form);
        boolean hadImages = !images.ids(LibraryImageService.Kind.LOCATION, id).isEmpty();
        images.update(LibraryImageService.Kind.LOCATION, id, added, removeImageIds, hadImages);
        admin.audit(adminId, "LOCATION_UPDATED", "location", id, l.getName(), ip);
    }

    @Transactional
    public void setLocationActive(long adminId, long id, boolean active, String ip) {
        LocationBible l = systemLocation(id);
        l.setActive(active);
        admin.audit(adminId, active ? "LOCATION_ACTIVATED" : "LOCATION_DEACTIVATED", "location", id, l.getName(), ip);
    }

    private LocationBible systemLocation(long id) {
        return locations.findByIdAndSystemPresetTrue(id).orElseThrow(() -> new NotFoundException("Mekân bulunamadı"));
    }

    private void apply(LocationBible l, LocationForm f) {
        String name = f.name().trim();
        String landmarks = f.recurringLandmarks() == null || f.recurringLandmarks().isBlank() ? null : f.recurringLandmarks().trim();
        rejectBlocked("name", name);
        rejectBlocked("environmentType", f.environmentType());
        rejectBlocked("atmosphereDescription", f.atmosphereDescription());
        rejectBlocked("visualAnchorTokens", f.visualAnchorTokens());
        rejectBlocked("lightingStyle", f.lightingStyle());
        rejectBlocked("recurringLandmarks", landmarks);
        if (locations.existsBySystemPresetTrueAndNameIgnoreCaseAndIdNot(name, l.getId() == null ? -1L : l.getId())) {
            throw new FieldValidationException("name", "Bu adla bir mekân zaten var");
        }
        l.setName(name);
        l.setEnvironmentType(f.environmentType().trim());
        l.setAtmosphereDescription(f.atmosphereDescription().trim());
        l.setVisualAnchorTokens(f.visualAnchorTokens().trim());
        l.setLightingStyle(f.lightingStyle().trim());
        l.setRecurringLandmarks(landmarks);
    }

    // ---------- Ortak ----------

    private static List<ImageRef> refs(List<Long> ids) {
        return ids.stream().map(i -> new ImageRef(i, LibraryImageService.url(i))).toList();
    }

    private Map<Long, Long> usage(String sql) {
        Map<Long, Long> out = new HashMap<>();
        jdbc.query(sql, rs -> {
            out.put(rs.getLong(1), rs.getLong(2));
        });
        return out;
    }

    private void rejectBlocked(String field, String text) {
        contentFilter.findBlockedWord(text).ifPresent(word -> {
            throw new FieldValidationException(field,
                    "\"" + word + "\" ifadesi çocuklar için uygun değil. Lütfen başka bir ifade kullanın.");
        });
    }
}
