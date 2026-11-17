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
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.exception.ConflictException;
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
    private final ContentFilterService contentFilter;
    private final MediaStorage storage;
    private final SaveQuotaService saveQuota;

    /** Başlık en fazla bu kadar karakter olabilir (ebeveyn düzenlemesi). */
    static final int MAX_TITLE = 160;

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
        return stories.findByParentIdAndSavedAtIsNotNullOrderByCreatedAtDesc(parentId).stream()
                .map(s -> StorySummaryResponse.from(s, withAudio.contains(s.getId()))).toList();
    }

    /** Yalnızca bu ebeveynin bu çocuğu için üretilmiş masallar (çocuğun sahipliğini çağıran ayrıca doğrular). */
    @Transactional(readOnly = true)
    public List<StorySummaryResponse> listForChild(Long parentId, Long childId) {
        Set<Long> withAudio = new HashSet<>(scenes.findStoryIdsWithFullAudio(parentId));
        return stories.findByParentIdAndChildIdAndSavedAtIsNotNullOrderByCreatedAtDesc(parentId, childId).stream()
                .map(s -> StorySummaryResponse.from(s, withAudio.contains(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public StoryDetailResponse get(Long parentId, Long storyId) {
        Story story = owned(parentId, storyId);
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

    /** "Son üretilen": süresi dolmamış en yeni geçici masal (childId null ise bütün çocuklar için). */
    @Transactional(readOnly = true)
    public java.util.Optional<StorySummaryResponse> latestUnsaved(Long parentId, Long childId) {
        java.util.Optional<Story> story = childId == null
                ? stories.findFirstByParentIdAndSavedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(parentId, StoryRetention.cutoff())
                : stories.findFirstByParentIdAndChildIdAndSavedAtIsNullAndCreatedAtAfterOrderByCreatedAtDesc(parentId, childId, StoryRetention.cutoff());
        return story.filter(s -> s.getStatus() != StoryEnums.Status.FAILED).map(StorySummaryResponse::from);
    }

    /**
     * "Bu masalı kaydet": kota denetimi (sunucuda), isteğe bağlı başlık düzeltmesi ve kaydetme.
     * Yalnızca hazırlanmış (COMPLETED) ve süresi dolmamış geçici masal kaydedilir; zaten kayıtlıysa yalnızca başlık güncellenir.
     */
    @Transactional
    public StorySummaryResponse save(Long parentId, Long storyId, String title) {
        Story story = owned(parentId, storyId);
        if (story.getStatus() != StoryEnums.Status.COMPLETED) {
            throw new ConflictException("Masal henüz hazır değil; hazır olunca kaydedebilirsiniz.");
        }
        if (title != null && !title.isBlank()) {
            story.setTitle(validTitle(title));
        }
        if (story.getSavedAt() == null) {
            saveQuota.ensureCanSave(parentId);
            story.setSavedAt(java.time.Instant.now());
        }
        return StorySummaryResponse.from(story);
    }

    /** "Kaydetme": geçici masal ve dosyaları silinir (kayıtlı masal bu yolla silinemez). Kredi iade edilmez (karar). */
    @Transactional
    public Long discard(Long parentId, Long storyId) {
        Story story = owned(parentId, storyId);
        if (story.getSavedAt() != null) {
            throw new ConflictException("Kaydedilmiş masal bu işlemle silinemez.");
        }
        Long childId = story.getChild() == null ? null : story.getChild().getId();
        stories.delete(story);
        deleteFilesAfterCommit(storyId);
        return childId;
    }

    /** Ebeveyn başlığı sonradan düzenler (güvenlik denetiminden geçer). */
    @Transactional
    public void rename(Long parentId, Long storyId, String title) {
        owned(parentId, storyId).setTitle(validTitle(title));
    }

    String validTitle(String raw) {
        String title = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new FieldValidationException("title", "Başlık 1-" + MAX_TITLE + " karakter olmalıdır");
        }
        contentFilter.findBlockedWord(title).ifPresent(w -> {
            throw new FieldValidationException("title", "Bu başlık masal kurallarına uymuyor; lütfen başka bir başlık yazın.");
        });
        return title;
    }

    /** Sahiplik ve geçici masalın süresi: süresi dolmuş geçici masal artık yokmuş gibi davranır. */
    private Story owned(Long parentId, Long storyId) {
        Story story = stories.findByIdAndParentId(storyId, parentId)
                .orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        if (StoryRetention.expired(story)) {
            throw new NotFoundException("Bu masalın süresi doldu; kaydedilmediği için silindi.");
        }
        return story;
    }

    private void deleteFilesAfterCommit(Long storyId) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        storage.deleteFolder("stories/" + storyId);
                    }
                });
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
