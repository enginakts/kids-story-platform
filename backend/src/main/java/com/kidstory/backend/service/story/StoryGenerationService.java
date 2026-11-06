package com.kidstory.backend.service.story;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryCharacter;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.PromptTemplateRepository;
import com.kidstory.backend.repository.StoryCharacterRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import com.kidstory.backend.service.BillingService;
import com.kidstory.backend.service.ai.AiRouter;
import com.kidstory.backend.service.ai.ImageClient;
import com.kidstory.backend.service.ai.LlmClient;
import com.kidstory.backend.service.ai.TtsClient;
import com.kidstory.backend.service.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Üretim hattı: STORY_TEXT → (her sahne için) SCENE_IMAGE + SCENE_AUDIO.
 * Yapay zekâ çağrıları veritabanı işlemi dışında yapılır; sonuçlar kısa işlemlerle kaydedilir.
 * Hata alan iş artan beklemeyle yeniden denenir; denemeler biterse masal FAILED olur ve kredi iade edilir.
 */
@Service
public class StoryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StoryGenerationService.class);
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);

    private final GenerationJobRepository jobs;
    private final StorySceneRepository scenes;
    private final StoryCharacterRepository storyCharacters;
    private final PromptTemplateRepository templates;
    private final ContentSafetyFilter safety;
    private final AiRouter ai;
    private final MediaStorage storage;
    private final BillingService billing;
    private final TransactionTemplate tx;

    StoryGenerationService(GenerationJobRepository jobs, StorySceneRepository scenes,
                           StoryCharacterRepository storyCharacters, PromptTemplateRepository templates,
                           ContentSafetyFilter safety, AiRouter ai, MediaStorage storage, BillingService billing,
                           PlatformTransactionManager transactions) {
        this.jobs = jobs;
        this.scenes = scenes;
        this.storyCharacters = storyCharacters;
        this.templates = templates;
        this.safety = safety;
        this.ai = ai;
        this.storage = storage;
        this.billing = billing;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Kuyruktan alınmış (RUNNING) bir işi çalıştırır. */
    public void process(Long jobId) {
        try {
            GenerationJob.Type type = tx.execute(s -> jobs.findById(jobId).orElseThrow().getJobType());
            switch (type) {
                case STORY_TEXT -> generateText(jobId);
                case SCENE_IMAGE -> generateImage(jobId);
                case SCENE_AUDIO -> generateAudio(jobId);
            }
        } catch (RuntimeException e) {
            log.warn("Üretim işi {} başarısız: {}", jobId, e.toString());
            tx.executeWithoutResult(s -> fail(jobId, e));
        }
    }

    private record TextInput(StoryPrompts.TextPrompt prompt, ContentSafetyFilter.Rules rules) {
    }

    private void generateText(Long jobId) {
        TextInput in = tx.execute(s -> {
            Story story = jobs.findById(jobId).orElseThrow().getStory();
            ContentSafetyFilter.Rules rules = safety.rules();
            String interests = safety.cleanOrNull(StoryPrompts.interests(story.getChild()), rules);
            return new TextInput(StoryPrompts.text(
                    templates.findByTemplateKeyAndActiveTrue(StoryPrompts.TEMPLATE_KEY).orElseThrow(),
                    story, story.getChild(), story.getPrimaryCharacter(), sides(story), story.getPrimaryLocation(),
                    interests), rules);
        });
        AiRouter.Outcome<String> out = ai.invoke(AiProviderConfig.Type.LLM, LlmClient.class,
                (client, config) -> client.complete(in.prompt().system(), in.prompt().user(), config.getTimeoutMs()));
        StoryDraftParser.Draft draft = StoryDraftParser.parse(out.value());
        // Güvenlik denetimi kaydetmeden önce yapılır: engellenen içerik çocuğa hiç ulaşmaz.
        String title = safety.clean(draft.title().trim(), in.rules());
        List<String> texts = draft.scenes().stream().map(sc -> safety.clean(sc.text().trim(), in.rules())).toList();

        tx.executeWithoutResult(s -> {
            GenerationJob job = jobs.findById(jobId).orElseThrow();
            Story story = job.getStory();
            CharacterBible main = story.getPrimaryCharacter();
            List<CharacterBible> sides = sides(story);
            long seed = StoryPrompts.seed(main, story);
            story.setTitle(title);
            story.setTotalScenes(texts.size());
            story.setStatus(StoryEnums.Status.GENERATING);
            for (int i = 0; i < texts.size(); i++) {
                String action = draft.scenes().get(i).imagePrompt();
                StoryScene scene = new StoryScene();
                scene.setStory(story);
                scene.setLocation(story.getPrimaryLocation());
                scene.setSceneNumber(i + 1);
                scene.setSceneText(texts.get(i));
                scene.setVoiceText(texts.get(i));
                scene.setImagePrompt(StoryPrompts.image(main, sides, story.getPrimaryLocation(),
                        action == null || action.isBlank() ? "smiling happily" : action.trim()));
                scene.setNegativePrompt(StoryPrompts.NEGATIVE);
                scene.setGenerationSeed(seed);
                scenes.save(scene);
                enqueue(story, scene, GenerationJob.Type.SCENE_IMAGE);
                enqueue(story, scene, GenerationJob.Type.SCENE_AUDIO);
            }
            succeed(job, out.providerName());
        });
    }

    private record ImageInput(String prompt, String negative, Long seed, String key) {
    }

    private void generateImage(Long jobId) {
        ImageInput in = tx.execute(s -> {
            StoryScene scene = jobs.findById(jobId).orElseThrow().getScene();
            return new ImageInput(scene.getImagePrompt(), scene.getNegativePrompt(), scene.getGenerationSeed(),
                    "stories/" + scene.getStory().getId() + "/scene-" + scene.getSceneNumber());
        });
        AiRouter.Outcome<ImageClient.GeneratedImage> out = ai.invoke(AiProviderConfig.Type.IMAGE, ImageClient.class,
                (client, config) -> client.generate(in.prompt(), in.negative(), in.seed(), config.getTimeoutMs()));
        if (out.value() == null || out.value().data() == null || out.value().data().length == 0) {
            throw new IllegalStateException("Boş görsel");
        }
        String key = in.key() + "." + safeExtension(out.value().extension(), "png");
        storage.save(key, out.value().data());
        tx.executeWithoutResult(s -> {
            GenerationJob job = jobs.findById(jobId).orElseThrow();
            StoryScene scene = job.getScene();
            scene.setImageUrl(key);
            if (scene.getSceneNumber() == 1) {
                job.getStory().setCoverImageUrl(key);
            }
            succeed(job, out.providerName());
        });
    }

    private record AudioInput(String text, String voiceId, String key) {
    }

    private void generateAudio(Long jobId) {
        AudioInput in = tx.execute(s -> {
            StoryScene scene = jobs.findById(jobId).orElseThrow().getScene();
            return new AudioInput(scene.getVoiceText(), scene.getStory().getPrimaryCharacter().getVoiceId(),
                    "stories/" + scene.getStory().getId() + "/scene-" + scene.getSceneNumber() + "-audio");
        });
        AiRouter.Outcome<TtsClient.SpeechResult> out = ai.invoke(AiProviderConfig.Type.TTS, TtsClient.class,
                (client, config) -> client.synthesize(in.text(), in.voiceId(), config.getTimeoutMs()));
        if (out.value() == null || out.value().audio() == null || out.value().audio().length == 0) {
            throw new IllegalStateException("Boş ses");
        }
        String key = in.key() + "." + safeExtension(out.value().extension(), "mp3");
        storage.save(key, out.value().audio());
        tx.executeWithoutResult(s -> {
            GenerationJob job = jobs.findById(jobId).orElseThrow();
            StoryScene scene = job.getScene();
            scene.setAudioUrl(key);
            scene.setAudioDurationSeconds(out.value().durationSeconds());
            scene.setTimestampsJson(out.value().timestampsJson() == null ? "[]" : out.value().timestampsJson());
            succeed(job, out.providerName());
        });
    }

    private void enqueue(Story story, StoryScene scene, GenerationJob.Type type) {
        GenerationJob job = new GenerationJob();
        job.setStory(story);
        job.setScene(scene);
        job.setJobType(type);
        jobs.save(job);
    }

    /** Masalın tüm işleri bittiyse masal tamamlanır. */
    private void succeed(GenerationJob job, String provider) {
        job.setStatus(GenerationJob.Status.SUCCEEDED);
        job.setProviderName(provider);
        job.setLastError(null);
        job.setFinishedAt(Instant.now());
        jobs.flush();
        Story story = job.getStory();
        if (story.getStatus() != StoryEnums.Status.FAILED
                && jobs.countByStoryIdAndStatusNot(story.getId(), GenerationJob.Status.SUCCEEDED) == 0) {
            story.setStatus(StoryEnums.Status.COMPLETED);
        }
    }

    private void fail(Long jobId, RuntimeException error) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        Story story = job.getStory();
        job.setLastError(truncate(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        if (story.getStatus() == StoryEnums.Status.FAILED) {
            job.setStatus(GenerationJob.Status.FAILED);
            job.setFinishedAt(Instant.now());
            return;
        }
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            job.setStatus(GenerationJob.Status.QUEUED);
            job.setRunAfter(Instant.now().plus(BASE_BACKOFF.multipliedBy(1L << Math.max(0, job.getAttemptCount() - 1))));
            return;
        }
        job.setStatus(GenerationJob.Status.FAILED);
        job.setFinishedAt(Instant.now());
        story.setStatus(StoryEnums.Status.FAILED);
        jobs.cancelQueued(story.getId(), "Masal başarısız olduğu için iptal edildi", Instant.now());
        billing.refundStoryCredit(story);
    }

    private List<CharacterBible> sides(Story story) {
        return storyCharacters.findByStoryIdOrderByIdAsc(story.getId()).stream().map(StoryCharacter::getCharacter).toList();
    }

    private static String safeExtension(String ext, String fallback) {
        return ext != null && ext.matches("[a-z0-9]{2,5}") ? ext : fallback;
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
