package com.kidstory.backend.service;

import com.kidstory.backend.dto.CharacterRequest;
import com.kidstory.backend.dto.CharacterResponse;
import com.kidstory.backend.dto.LocationResponse;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.CharacterBibleRepository;
import com.kidstory.backend.repository.ChildProfileRepository;
import com.kidstory.backend.repository.LocationBibleRepository;
import com.kidstory.backend.repository.ParentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Karakter rehberi (ebeveyne ait) ve sistem mekân şablonları. parentId her zaman oturumdan/token'dan gelir. */
@Service
@RequiredArgsConstructor
public class CharacterService {

    static final String MAX_SETTING = "character.max_per_parent";
    private static final int DEFAULT_MAX = 20;

    private final CharacterBibleRepository characters;
    private final LocationBibleRepository locations;
    private final ChildProfileRepository children;
    private final ParentRepository parents;
    private final ContentFilterService contentFilter;
    private final SettingsService settings;

    @Transactional(readOnly = true)
    public List<CharacterResponse> own(Long parentId) {
        return characters.findByParentIdAndActiveTrueOrderByCreatedAtDesc(parentId).stream()
                .map(CharacterResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> presets() {
        return characters.findBySystemPresetTrueAndActiveTrueOrderByIdAsc().stream()
                .map(CharacterResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CharacterResponse get(Long parentId, Long id) {
        return CharacterResponse.from(owned(parentId, id));
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> locations() {
        return locations.findBySystemPresetTrueOrderByIdAsc().stream().map(LocationResponse::from).toList();
    }

    @Transactional
    public CharacterResponse create(Long parentId, CharacterRequest request) {
        int max = settings.intValue(MAX_SETTING, DEFAULT_MAX);
        if (characters.countByParentIdAndActiveTrue(parentId) >= max) {
            throw new ConflictException("En fazla " + max + " karakter oluşturabilirsiniz. Yenisi için birini kaldırın.");
        }
        CharacterBible character = new CharacterBible();
        character.setParent(parents.getReferenceById(parentId));
        apply(parentId, character, request);
        return CharacterResponse.from(characters.save(character));
    }

    @Transactional
    public CharacterResponse update(Long parentId, Long id, CharacterRequest request) {
        CharacterBible character = owned(parentId, id);
        apply(parentId, character, request);
        return CharacterResponse.from(character);
    }

    /** Masallarda kullanılmış olabileceği için kayıt silinmez, pasife alınır. */
    @Transactional
    public void deactivate(Long parentId, Long id) {
        owned(parentId, id).setActive(false);
    }

    private CharacterBible owned(Long parentId, Long id) {
        return characters.findByIdAndParentIdAndActiveTrue(id, parentId)
                .orElseThrow(() -> new NotFoundException("Karakter bulunamadı"));
    }

    private void apply(Long parentId, CharacterBible c, CharacterRequest r) {
        String name = r.name().trim();
        String personality = r.personalityTraits() == null || r.personalityTraits().isBlank()
                ? null : r.personalityTraits().trim();

        rejectBlocked("name", name);
        rejectBlocked("personalityTraits", personality);
        for (ChildProfile child : children.findByParentIdAndActiveTrueOrderByCreatedAtAsc(parentId)) {
            if (ContentFilterService.containsWord(name, child.getName())) {
                throw new FieldValidationException("name",
                        "Karakter adı çocuğunuzun adını içeremez. Masallarda çocuğunuzun kendisi zaten yer alabilir.");
            }
        }

        var species = CharacterCatalog.parse(CharacterCatalog.Species.class, r.speciesCode(), "speciesCode", null);
        var color = CharacterCatalog.parse(CharacterCatalog.Color.class, r.colorCode(), "colorCode", null);
        var outfit = CharacterCatalog.parse(CharacterCatalog.Outfit.class, r.outfitCode(), "outfitCode",
                CharacterCatalog.Outfit.YOK);
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

    private void rejectBlocked(String field, String text) {
        contentFilter.findBlockedWord(text).ifPresent(word -> {
            throw new FieldValidationException(field,
                    "\"" + word + "\" ifadesi çocuklar için uygun değil. Lütfen başka bir ifade kullanın.");
        });
    }
}
