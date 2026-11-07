package com.kidstory.backend.service;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.PromptTemplate;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryCharacter;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.PromptTemplateRepository;
import com.kidstory.backend.repository.StoryCharacterRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import com.kidstory.backend.service.ai.AiRouter;
import com.kidstory.backend.service.ai.LlmClient;
import com.kidstory.backend.service.ai.LlmRequest;
import com.kidstory.backend.service.ai.RateLimitedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Masal üretim işinin adımları: 1) Trendyol LLM metni yazar, 2) Gemini sahnelere böler.
 * Dış çağrılar veritabanı işlemi dışında yapılır; hazırlık ve sonuç kaydı kısa işlemlerdir.
 */
@Service
@RequiredArgsConstructor
public class StoryGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StoryGenerationService.class);

    private final GenerationJobRepository jobs;
    private final StoryCharacterRepository storyCharacters;
    private final StorySceneRepository scenes;
    private final PromptTemplateRepository templates;
    private final StoryDraftParser parser;
    private final AiRouter router;
    private final BillingService billing;

    /**
     * textRequest hazırdır; sahne isteği 1. aşamanın metniyle kurulur (sceneRequest).
     */
    public record Prepared(Long jobId, LlmRequest textRequest, Function<StoryText, LlmRequest> sceneRequest,
                           boolean childAllowed, String placeholder) {
    }

    public record Generated(StoryDraft draft, String provider) {
    }

    @Transactional
    public Prepared prepare(Long jobId) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        Story story = job.getStory();
        story.setStatus(StoryEnums.Status.GENERATING);
        List<CharacterBible> sides = sides(story);
        PromptTemplate textTemplate = template(StoryPromptBuilder.TEXT_TEMPLATE);
        PromptTemplate sceneTemplate = template(StoryPromptBuilder.SCENE_TEMPLATE);
        LlmRequest textRequest = StoryPromptBuilder.text(textTemplate, story, sides);
        // Sahne isteği için gereken alanlar işlem içinde okunur; fonksiyon işlem dışında güvenle çalışır.
        LlmRequest sceneProbe = StoryPromptBuilder.scenes(sceneTemplate, story, sides, new StoryText("\u0000T", "\u0000S"));
        Function<StoryText, LlmRequest> sceneRequest = text -> new LlmRequest(
                sceneProbe.systemPrompt(),
                sceneProbe.userPrompt().replace("\u0000T", text.title()).replace("\u0000S", text.body()),
                sceneProbe.schemaName(), sceneProbe.schema());
        return new Prepared(jobId, textRequest, sceneRequest, story.isPersonalizedUsed(), StoryPromptBuilder.placeholder(story, sides));
    }

    /** İşlem dışında: iki aşamalı LLM çağrısı ve doğrulama. Hata fırlatırsa iş yeniden denenir. */
    public Generated generate(Prepared prepared) {
        AtomicReference<String> textProvider = new AtomicReference<>();
        String rawText = router.call(AiProviderConfig.Type.LLM, LlmClient.class, (client, config) -> {
            textProvider.set(config.getProviderName());
            return client.complete(prepared.textRequest(), config);
        });
        StoryText text = parser.parseText(rawText, prepared.childAllowed(), prepared.placeholder());

        AtomicReference<String> sceneProvider = new AtomicReference<>();
        LlmRequest sceneRequest = prepared.sceneRequest().apply(text);
        String rawScenes = router.call(AiProviderConfig.Type.SCENE, LlmClient.class, (client, config) -> {
            sceneProvider.set(config.getProviderName());
            return client.complete(sceneRequest, config);
        });
        StoryDraft draft = parser.parse(rawScenes, prepared.childAllowed(), prepared.placeholder());
        return new Generated(draft, textProvider.get() + "+" + sceneProvider.get());
    }

    @Transactional
    public void complete(Prepared prepared, Generated generated) {
        GenerationJob job = jobs.findById(prepared.jobId()).orElseThrow();
        Story story = job.getStory();
        List<CharacterBible> sides = sides(story);
        String childName = story.getChild().getName();
        StoryDraft draft = generated.draft();

        story.setTitle(personalize(draft.title(), prepared, childName));
        int number = 1;
        for (StoryDraft.Scene s : draft.scenes()) {
            String text = personalize(s.text().trim(), prepared, childName);
            StoryScene scene = new StoryScene();
            scene.setStory(story);
            scene.setLocation(story.getPrimaryLocation());
            scene.setSceneNumber(number++);
            scene.setSceneText(text);
            scene.setVoiceText(text);
            scene.setImagePrompt(StoryPromptBuilder.imagePrompt(s.imagePrompt(), story, sides, prepared.placeholder()));
            scenes.save(scene);
        }
        story.setTotalScenes(draft.scenes().size());
        // Görsel (Adım 6) ve ses (Adım 7) eklenene kadar metin hazır olduğunda masal tamamlanmış sayılır.
        story.setStatus(StoryEnums.Status.COMPLETED);

        job.setStatus(GenerationJob.Status.SUCCEEDED);
        job.setProviderName(generated.provider());
        job.setFinishedAt(Instant.now());
        job.setLastError(null);
    }

    /** Deneme hakkı varsa iş yeniden kuyruğa girer; yoksa masal başarısız olur ve kredi iade edilir. */
    @Transactional
    public void fail(Long jobId, Exception error) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        Story story = job.getStory();
        // AiRouter sağlayıcı hatasını sarar; asıl neden ve hız sınırı zincirin içinden bulunur.
        Throwable root = error;
        RateLimitedException rateLimited = null;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RateLimitedException r) {
                rateLimited = r;
            }
            root = t;
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        job.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
        if (job.getAttemptCount() < job.getMaxAttempts()) {
            long delay = rateLimited != null ? rateLimited.getRetryAfterSeconds() : 15L * job.getAttemptCount();
            job.setStatus(GenerationJob.Status.QUEUED);
            job.setRunAfter(Instant.now().plus(Duration.ofSeconds(delay)));
            log.info("Masal üretimi yeniden denenecek (job={}, deneme={}): {}", jobId, job.getAttemptCount(), message);
            return;
        }
        job.setStatus(GenerationJob.Status.FAILED);
        job.setFinishedAt(Instant.now());
        story.setStatus(StoryEnums.Status.FAILED);
        billing.refundStoryCredit(story.getParent().getId(), story);
        log.warn("Masal üretimi başarısız (job={}, story={}): {}", jobId, story.getId(), message);
    }

    private PromptTemplate template(String key) {
        return templates.findByTemplateKeyAndActiveTrue(key).orElseThrow();
    }

    private List<CharacterBible> sides(Story story) {
        return storyCharacters.findByStoryIdOrderByIdAsc(story.getId()).stream().map(StoryCharacter::getCharacter).toList();
    }

    private static String personalize(String text, Prepared prepared, String childName) {
        return prepared.childAllowed() ? ChildNamePersonalizer.apply(text, prepared.placeholder(), childName) : text;
    }
}
