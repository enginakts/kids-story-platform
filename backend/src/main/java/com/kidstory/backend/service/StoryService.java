package com.kidstory.backend.service;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.dto.StoryRequest;
import com.kidstory.backend.dto.StorySummaryResponse;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.entity.StoryCharacter;
import com.kidstory.backend.exception.EmailNotVerifiedException;
import com.kidstory.backend.exception.FieldValidationException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.CharacterBibleRepository;
import com.kidstory.backend.repository.ChildProfileRepository;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.LocationBibleRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.StoryCharacterRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Masal isteği, kütüphane ve masal görüntüleme. parentId her zaman oturumdan/token'dan gelir. */
@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository stories;
    private final StorySceneRepository scenes;
    private final StoryCharacterRepository storyCharacters;
    private final GenerationJobRepository jobs;
    private final ParentRepository parents;
    private final ChildProfileRepository children;
    private final CharacterBibleRepository characters;
    private final LocationBibleRepository locations;
    private final BillingService billing;
    private final StoryAudioService audio;
    private final StoryImageService images;

    /** Krediyi harcar, masalı ve metin üretim işini kuyruğa koyar. Üretim arka planda yapılır. */
    @Transactional
    public StorySummaryResponse request(Long parentId, StoryRequest r) {
        Parent parent = parents.findById(parentId).orElseThrow();
        if (!parent.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        ChildProfile child = children.findByIdAndParentIdAndActiveTrue(r.childId(), parentId)
                .orElseThrow(() -> new FieldValidationException("childId", "Çocuk profili bulunamadı"));
        CharacterBible primary = usableCharacter(r.primaryCharacterId(), "primaryCharacterId");
        Set<Long> sideIds = new LinkedHashSet<>(r.sideCharacterIds() == null ? List.of() : r.sideCharacterIds());
        sideIds.remove(primary.getId());
        if (sideIds.size() > 2) {
            throw new FieldValidationException("sideCharacterIds", "En fazla 2 yan karakter seçebilirsiniz");
        }
        List<CharacterBible> sides = sideIds.stream().map(id -> usableCharacter(id, "sideCharacterIds")).toList();
        LocationBible location = (r.locationId() == null ? java.util.Optional.<LocationBible>empty()
                : locations.findByIdAndSystemPresetTrueAndActiveTrue(r.locationId()))
                .orElseThrow(() -> new FieldValidationException("locationId", "Mekân bulunamadı"));

        Story story = new Story();
        story.setParent(parent);
        story.setChild(child);
        story.setPrimaryCharacter(primary);
        story.setPrimaryLocation(location);
        story.setTitle("Yeni masal");
        story.setTheme(r.theme());
        story.setNarrationStyle(r.narrationStyle());
        story.setTotalScenes(r.length().scenes());
        story.setTargetDurationMinutes(r.length().minutes());
        story.setPersonalizedUsed(r.includeChild());
        stories.save(story);

        for (CharacterBible side : sides) {
            StoryCharacter link = new StoryCharacter();
            link.setStory(story);
            link.setCharacter(side);
            storyCharacters.save(link);
        }

        billing.spendStoryCredit(parentId, story);

        GenerationJob job = new GenerationJob();
        job.setStory(story);
        job.setJobType(GenerationJob.Type.STORY_TEXT);
        jobs.save(job);
        return StorySummaryResponse.from(story);
    }

    @Transactional(readOnly = true)
    public List<StorySummaryResponse> list(Long parentId) {
        Set<Long> withAudio = new HashSet<>(scenes.findStoryIdsWithFullAudio(parentId));
        return stories.findByParentIdOrderByCreatedAtDesc(parentId).stream()
                .map(s -> StorySummaryResponse.from(s, withAudio.contains(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse get(Long parentId, Long storyId) {
        Story story = stories.findByIdAndParentId(storyId, parentId)
                .orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        List<StoryScene> sceneList = scenes.findByStoryIdOrderBySceneNumberAsc(storyId);
        List<StoryDetailResponse.Scene> list = sceneList.stream()
                .map(s -> new StoryDetailResponse.Scene(s.getSceneNumber(), s.getSceneText(),
                        s.getImageUrl() == null ? null : "/api/v1/stories/" + storyId + "/scenes/" + s.getSceneNumber() + "/image",
                        s.getAudioUrl() == null ? null : "/api/v1/stories/" + storyId + "/scenes/" + s.getSceneNumber() + "/audio",
                        s.getAudioDurationSeconds()))
                .toList();
        StoryDetailResponse.Progress audioStatus = audio.status(storyId, sceneList);
        return new StoryDetailResponse(StorySummaryResponse.from(story, "READY".equals(audioStatus.status())),
                StoryCatalog.style(story.getNarrationStyle()), list, audioStatus, images.status(storyId, sceneList));
    }

    @Transactional(readOnly = true)
    public boolean emailVerified(Long parentId) {
        return parents.findById(parentId).map(Parent::isEmailVerified).orElse(false);
    }

    /** Yalnızca yöneticilerin tanımladığı, yayındaki karakterler seçilebilir (V10). */
    private CharacterBible usableCharacter(Long id, String field) {
        if (id == null) {
            throw new FieldValidationException(field, "Lütfen bir karakter seçin");
        }
        return characters.findByIdAndSystemPresetTrueAndActiveTrue(id)
                .orElseThrow(() -> new FieldValidationException(field, "Karakter bulunamadı"));
    }
}
