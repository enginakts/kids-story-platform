package com.kidstory.backend.service.story;

import com.kidstory.backend.dto.CreateStoryRequest;
import com.kidstory.backend.dto.StoryViews;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.ChildProfile;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.LocationBible;
import com.kidstory.backend.entity.Parent;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryCharacter;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.CharacterBibleRepository;
import com.kidstory.backend.repository.ChildProfileRepository;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.LocationBibleRepository;
import com.kidstory.backend.repository.ParentRepository;
import com.kidstory.backend.repository.StoryCharacterRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import com.kidstory.backend.service.BillingService;
import com.kidstory.backend.service.storage.MediaStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Masal oluşturma ve okuma. parentId her zaman oturumdan veya token'dan gelir;
 * masallar çocuğa bağlıdır ve yalnızca o çocuğun ebeveyni görebilir.
 */
@Service
public class StoryService {

    private final StoryRepository stories;
    private final StorySceneRepository scenes;
    private final StoryCharacterRepository storyCharacters;
    private final ChildProfileRepository children;
    private final ParentRepository parents;
    private final CharacterBibleRepository characters;
    private final LocationBibleRepository locations;
    private final GenerationJobRepository jobs;
    private final BillingService billing;
    private final MediaStorage storage;
    private final boolean requireVerifiedEmail;

    StoryService(StoryRepository stories, StorySceneRepository scenes, StoryCharacterRepository storyCharacters,
                 ChildProfileRepository children, ParentRepository parents, CharacterBibleRepository characters,
                 LocationBibleRepository locations, GenerationJobRepository jobs, BillingService billing,
                 MediaStorage storage, @Value("${app.account.email-verification.enabled}") boolean requireVerifiedEmail) {
        this.stories = stories;
        this.scenes = scenes;
        this.storyCharacters = storyCharacters;
        this.children = children;
        this.parents = parents;
        this.characters = characters;
        this.locations = locations;
        this.jobs = jobs;
        this.billing = billing;
        this.storage = storage;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    public enum Media { IMAGE, AUDIO, COVER }

    @Transactional(readOnly = true)
    public StoryViews.Options options(Long parentId, Long childId) {
        owned(parentId, childId);
        return new StoryViews.Options(
                characters.findAvailable(childId).stream().map(StoryViews.CharacterOption::from).toList(),
                locations.findBySystemPresetTrueOrderById().stream().map(StoryViews.LocationOption::from).toList(),
                StoryViews.EnumOption.of(StoryEnums.Theme.class),
                StoryViews.EnumOption.of(StoryEnums.Situation.class),
                StoryViews.EnumOption.of(StoryEnums.Emotion.class),
                StoryViews.EnumOption.of(StoryEnums.NarrationStyle.class));
    }

    /** Krediyi düşer, masalı PENDING olarak kaydeder ve metin üretim işini kuyruğa ekler. */
    @Transactional
    public StoryViews.Detail create(Long parentId, CreateStoryRequest r) {
        Parent parent = parents.findById(parentId).orElseThrow();
        if (requireVerifiedEmail && !parent.isEmailVerified()) {
            throw new ConflictException("Masal oluşturmak için önce e-posta adresinizi doğrulayın");
        }
        ChildProfile child = owned(parentId, r.childId());
        Map<Long, CharacterBible> available = characters.findAvailable(child.getId()).stream()
                .collect(Collectors.toMap(CharacterBible::getId, Function.identity()));
        CharacterBible main = Optional.ofNullable(available.get(r.characterId()))
                .orElseThrow(() -> new NotFoundException("Karakter bulunamadı"));
        List<CharacterBible> sides = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(r.sideCharacterIds() == null ? List.of() : r.sideCharacterIds())) {
            if (!id.equals(main.getId())) {
                sides.add(Optional.ofNullable(available.get(id)).orElseThrow(() -> new NotFoundException("Karakter bulunamadı")));
            }
        }
        LocationBible location = locations.findById(r.locationId()).filter(LocationBible::isSystemPreset)
                .orElseThrow(() -> new NotFoundException("Mekân bulunamadı"));

        int sceneCount = r.sceneCount() == null ? 4 : r.sceneCount();
        Story story = new Story();
        story.setChild(child);
        story.setParent(parent);
        story.setPrimaryCharacter(main);
        story.setPrimaryLocation(location);
        story.setTitle("Masal hazırlanıyor");
        story.setTheme(r.theme());
        story.setSituation(r.situation());
        story.setTargetEmotion(r.emotion());
        story.setNarrationStyle(r.narrationStyle() == null ? StoryEnums.NarrationStyle.WARM : r.narrationStyle());
        story.setTotalScenes(sceneCount);
        story.setTargetDurationMinutes(sceneCount - 2);
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
        return detail(story);
    }

    @Transactional(readOnly = true)
    public List<StoryViews.Summary> listForParent(Long parentId) {
        return stories.findByParentIdOrderByCreatedAtDesc(parentId).stream().map(StoryService::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<StoryViews.Summary> listForChild(Long parentId, Long childId) {
        owned(parentId, childId);
        return stories.findByChildIdAndParentIdOrderByCreatedAtDesc(childId, parentId).stream()
                .map(StoryService::summary).toList();
    }

    @Transactional(readOnly = true)
    public StoryViews.Detail get(Long parentId, Long storyId) {
        return detail(ownedStory(parentId, storyId));
    }

    /** Dosya yalnızca masalın sahibi olan ebeveyne sunulur. */
    @Transactional(readOnly = true)
    public Optional<Resource> media(Long parentId, Long storyId, int sceneNumber, Media type) {
        Story story = ownedStory(parentId, storyId);
        String key = switch (type) {
            case COVER -> story.getCoverImageUrl();
            case IMAGE -> scenes.findByStoryIdAndSceneNumber(storyId, sceneNumber).map(StoryScene::getImageUrl).orElse(null);
            case AUDIO -> scenes.findByStoryIdAndSceneNumber(storyId, sceneNumber).map(StoryScene::getAudioUrl).orElse(null);
        };
        return key == null ? Optional.empty() : storage.load(key);
    }

    private ChildProfile owned(Long parentId, Long childId) {
        return children.findByIdAndParentIdAndActiveTrue(childId, parentId)
                .orElseThrow(() -> new NotFoundException("Çocuk profili bulunamadı"));
    }

    private Story ownedStory(Long parentId, Long storyId) {
        return stories.findByIdAndParentId(storyId, parentId)
                .filter(s -> s.getChild().isActive())
                .orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
    }

    private static StoryViews.Summary summary(Story s) {
        return new StoryViews.Summary(s.getId(), s.getChild().getId(), s.getChild().getName(), s.getTitle(), s.getStatus(),
                s.getTheme().getLabel(), s.getCoverImageUrl() != null, s.getCreatedAt());
    }

    private StoryViews.Detail detail(Story s) {
        List<StoryViews.Scene> sceneViews = scenes.findByStoryIdOrderBySceneNumberAsc(s.getId()).stream()
                .map(sc -> new StoryViews.Scene(sc.getSceneNumber(), sc.getSceneText(), sc.getImageUrl() != null,
                        sc.getAudioUrl() != null, sc.getAudioDurationSeconds(), sc.getTimestampsJson()))
                .toList();
        List<String> sideNames = storyCharacters.findByStoryIdOrderByIdAsc(s.getId()).stream()
                .map(link -> link.getCharacter().getName()).toList();
        return new StoryViews.Detail(s.getId(), s.getChild().getId(), s.getChild().getName(), s.getTitle(), s.getStatus(),
                s.getTheme().getLabel(), label(s.getSituation()), label(s.getTargetEmotion()),
                s.getNarrationStyle().getLabel(),
                s.getPrimaryCharacter() == null ? null : s.getPrimaryCharacter().getName(), sideNames,
                s.getPrimaryLocation() == null ? null : s.getPrimaryLocation().getName(),
                s.getTotalScenes(), progress(s), s.getCoverImageUrl() != null, s.getCreatedAt(), sceneViews);
    }

    /** Toplam iş: 1 metin + her sahne için 1 görsel ve 1 ses. */
    private int progress(Story s) {
        if (s.getStatus() == StoryEnums.Status.COMPLETED) {
            return 100;
        }
        int total = 1 + 2 * s.getTotalScenes();
        long done = jobs.countByStoryIdAndStatus(s.getId(), GenerationJob.Status.SUCCEEDED);
        return (int) Math.min(99, done * 100 / total);
    }

    private static String label(StoryEnums.Labeled value) {
        return value == null ? null : value.getLabel();
    }
}
