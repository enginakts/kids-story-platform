package com.kidstory.backend.service;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import com.kidstory.backend.service.ai.AiRouter;
import com.kidstory.backend.service.ai.TtsClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adım 7: sahnelerin seslendirilmesi. Masal metni hazır olunca her sahne için bir SCENE_AUDIO işi kuyruğa girer;
 * ses hazırlanırken masal okunabilir. Ses hazırlanamazsa masal geçerli kalır, kredi iade edilmez,
 * ebeveyn sesi yeniden isteyebilir.
 */
@Service
@RequiredArgsConstructor
public class StoryAudioService {

    private static final Logger log = LoggerFactory.getLogger(StoryAudioService.class);

    /** Kikiri modelinin tek hazır sesi. Karakter başına ses (voice_id) model yeni sesler sununca kullanılacak. */
    public static final String DEFAULT_VOICE = "nisan";

    private final GenerationJobRepository jobs;
    private final StorySceneRepository scenes;
    private final StoryRepository stories;
    private final AiRouter router;
    private final MediaStorage storage;

    public record Prepared(Long jobId, Long sceneId, Long storyId, int sceneNumber, String text, double speed) {
    }

    public record Generated(TtsClient.SpeechResult speech, String provider) {
    }

    public record AudioFile(Resource resource, String mimeType) {
    }

    /** Açık bir işlem içinde çağrılır: sesi olmayan ve bekleyen işi bulunmayan sahneler için iş açar. */
    public int enqueue(Story story, List<StoryScene> list) {
        Map<Long, GenerationJob.Status> latest = latestAudioJobs(story.getId());
        int created = 0;
        for (StoryScene scene : list) {
            GenerationJob.Status last = latest.get(scene.getId());
            boolean active = last == GenerationJob.Status.QUEUED || last == GenerationJob.Status.RUNNING;
            if (scene.getAudioUrl() != null || active) {
                continue;
            }
            GenerationJob job = new GenerationJob();
            job.setStory(story);
            job.setScene(scene);
            job.setJobType(GenerationJob.Type.SCENE_AUDIO);
            jobs.save(job);
            created++;
        }
        return created;
    }

    /** Ebeveyn isteği: hazırlanamamış (veya hiç istenmemiş) sahne seslerini yeniden kuyruğa koyar. */
    @Transactional
    public int requestAudio(Long parentId, Long storyId) {
        Story story = stories.findByIdAndParentId(storyId, parentId).orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        if (story.getStatus() != StoryEnums.Status.COMPLETED) {
            throw new ConflictException("Ses, masal hazır olduktan sonra hazırlanabilir");
        }
        return enqueue(story, scenes.findByStoryIdOrderBySceneNumberAsc(storyId));
    }

    @Transactional
    public Prepared prepare(Long jobId) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        StoryScene scene = job.getScene();
        Story story = job.getStory();
        return new Prepared(jobId, scene.getId(), story.getId(), scene.getSceneNumber(), spoken(scene.getVoiceText()),
                speed(story.getNarrationStyle()));
    }

    /** İşlem dışında: sağlayıcı çağrısı. Hata fırlatırsa iş yeniden denenir. */
    public Generated synthesize(Prepared prepared) {
        AtomicReference<String> provider = new AtomicReference<>();
        TtsClient.SpeechResult speech = router.call(AiProviderConfig.Type.TTS, TtsClient.class, (client, config) -> {
            provider.set(config.getProviderName());
            return client.synthesize(new TtsClient.SpeechRequest(prepared.text(), DEFAULT_VOICE, prepared.speed()), config);
        });
        if (speech.audio() == null || speech.audio().length == 0) {
            throw new IllegalStateException("Ses sağlayıcısı boş dosya döndü");
        }
        return new Generated(speech, provider.get());
    }

    @Transactional
    public void complete(Prepared prepared, Generated generated) {
        GenerationJob job = jobs.findById(prepared.jobId()).orElseThrow();
        StoryScene scene = scenes.findById(prepared.sceneId()).orElseThrow();
        String extension = "audio/wav".equals(generated.speech().mimeType()) ? ".wav" : ".mp3";
        // Rastgele ek: ses yenilenince tarayıcı önbelleğindeki eski dosya kullanılmaz.
        String key = "stories/" + prepared.storyId() + "/scene-" + prepared.sceneNumber() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + extension;
        storage.store(key, generated.speech().audio());
        String old = scene.getAudioUrl();
        scene.setAudioUrl(key);
        scene.setAudioDurationSeconds(generated.speech().durationSeconds());
        scene.setTimestampsJson(generated.speech().timestampsJson());
        if (old != null) {
            storage.delete(old);
        }
        job.setStatus(GenerationJob.Status.SUCCEEDED);
        job.setProviderName(generated.provider());
        job.setFinishedAt(Instant.now());
        job.setLastError(null);
    }

    @Transactional
    public void fail(Long jobId, Exception error) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        if (JobRetry.reschedule(job, error, 30)) {
            log.info("Sahne sesi yeniden denenecek (job={}, deneme={}): {}", jobId, job.getAttemptCount(), job.getLastError());
        } else {
            log.warn("Sahne sesi hazırlanamadı (job={}, story={}): {}", jobId, job.getStory().getId(), job.getLastError());
        }
    }

    /** Masal sayfası ve API için seslendirme durumu. */
    @Transactional(readOnly = true)
    public StoryDetailResponse.Progress status(Long storyId, List<StoryScene> list) {
        return MediaProgress.of(latestAudioJobs(storyId), list, s -> s.getAudioUrl() != null);
    }

    /** Sahiplik denetimiyle sahne sesi. Başka ebeveynin masalı için NotFound. */
    @Transactional(readOnly = true)
    public AudioFile file(Long parentId, Long storyId, int sceneNumber) {
        stories.findByIdAndParentId(storyId, parentId).orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        StoryScene scene = scenes.findByStoryIdOrderBySceneNumberAsc(storyId).stream()
                .filter(s -> s.getSceneNumber() == sceneNumber && s.getAudioUrl() != null)
                .findFirst().orElseThrow(() -> new NotFoundException("Ses bulunamadı"));
        Resource resource = storage.load(scene.getAudioUrl()).orElseThrow(() -> new NotFoundException("Ses bulunamadı"));
        return new AudioFile(resource, scene.getAudioUrl().endsWith(".wav") ? "audio/wav" : "audio/mpeg");
    }

    private Map<Long, GenerationJob.Status> latestAudioJobs(Long storyId) {
        Map<Long, GenerationJob.Status> latest = new HashMap<>();
        for (GenerationJob job : jobs.findByStoryIdAndJobTypeOrderByIdAsc(storyId, GenerationJob.Type.SCENE_AUDIO)) {
            if (job.getScene() != null) {
                latest.put(job.getScene().getId(), job.getStatus());
            }
        }
        return latest;
    }

    /** Anlatım hızı: yatmadan önce masalı daha yavaş, oyunbaz masal normal hızda okunur. */
    static double speed(StoryEnums.NarrationStyle style) {
        return switch (style) {
            case BEDTIME -> 0.88;
            case PLAYFUL -> 1.0;
            case WARM, RHYMING -> 0.95;
        };
    }

    /** Seslendirmede okunmayacak işaretler atılır; tırnak ve tireler duraksamaya dönüşür. */
    static String spoken(String text) {
        return text.replaceAll("[\"“”«»*_#]", "").replaceAll("\\s+[-–—]\\s+", ", ").replaceAll("\\s+", " ").trim();
    }
}
