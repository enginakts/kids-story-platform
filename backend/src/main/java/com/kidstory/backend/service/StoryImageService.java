package com.kidstory.backend.service;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.entity.CharacterBible;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.Story;
import com.kidstory.backend.entity.StoryCharacter;
import com.kidstory.backend.entity.StoryEnums;
import com.kidstory.backend.entity.StoryScene;
import com.kidstory.backend.exception.ConflictException;
import com.kidstory.backend.exception.NotFoundException;
import com.kidstory.backend.repository.CharacterBibleRepository;
import com.kidstory.backend.repository.GenerationJobRepository;
import com.kidstory.backend.repository.StoryCharacterRepository;
import com.kidstory.backend.repository.StoryRepository;
import com.kidstory.backend.repository.StorySceneRepository;
import com.kidstory.backend.service.ai.AiRouter;
import com.kidstory.backend.service.ai.ImageClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Adım 6: sahne resimleri. Masal metni hazır olunca her sahne için bir SCENE_IMAGE işi kuyruğa girer.
 * Karakter tutarlılığı: her karakter için bir kez referans portre üretilir (character_bibles.reference_image_url)
 * ve sonraki tüm sahnelere referans görsel olarak verilir. Görsel prompt'unda çocuğun adı yoktur.
 * Resim hazırlanamazsa masal geçerli kalır, kredi iade edilmez, ebeveyn resimleri yeniden isteyebilir.
 */
@Service
@RequiredArgsConstructor
public class StoryImageService {

    private static final Logger log = LoggerFactory.getLogger(StoryImageService.class);
    /** Sağlayıcı referans görsellerin 512 pikselden küçük olmasını ister. */
    static final int REFERENCE_SIDE = 448;
    static final int MAX_REFERENCES = 3;

    private final GenerationJobRepository jobs;
    private final StorySceneRepository scenes;
    private final StoryRepository stories;
    private final StoryCharacterRepository storyCharacters;
    private final CharacterBibleRepository characters;
    private final AiRouter router;
    private final MediaStorage storage;

    @Value("${app.image.width:1024}")
    private int width;
    @Value("${app.image.height:768}")
    private int height;
    @Value("${app.image.character-references:true}")
    private boolean useReferences;

    /** Masaldaki bir karakter: referans portresi yoksa iş sırasında üretilir. */
    public record Cast(Long characterId, String anchors, long seed, String referenceKey) {
    }

    public record Prepared(Long jobId, Long sceneId, Long storyId, int sceneNumber, String prompt, long seed, List<Cast> cast) {
    }

    /** newReferences: bu işte üretilen karakter portreleri (karakter kimliği -> dosya anahtarı). */
    public record Generated(ImageClient.ImageResult image, String provider, Map<Long, String> newReferences) {
    }

    public record ImageFile(Resource resource, String mimeType) {
    }

    /** Açık bir işlem içinde çağrılır: resmi olmayan ve bekleyen işi bulunmayan sahneler için iş açar. */
    public int enqueue(Story story, List<StoryScene> list) {
        Map<Long, GenerationJob.Status> latest = latestJobs(story.getId());
        int created = 0;
        for (StoryScene scene : list) {
            GenerationJob.Status last = latest.get(scene.getId());
            if (scene.getImageUrl() != null || last == GenerationJob.Status.QUEUED || last == GenerationJob.Status.RUNNING) {
                continue;
            }
            GenerationJob job = new GenerationJob();
            job.setStory(story);
            job.setScene(scene);
            job.setJobType(GenerationJob.Type.SCENE_IMAGE);
            jobs.save(job);
            created++;
        }
        return created;
    }

    @Transactional
    public int requestImages(Long parentId, Long storyId) {
        Story story = stories.findByIdAndParentId(storyId, parentId).orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        if (story.getStatus() != StoryEnums.Status.COMPLETED) {
            throw new ConflictException("Resimler, masal hazır olduktan sonra hazırlanabilir");
        }
        return enqueue(story, scenes.findByStoryIdOrderBySceneNumberAsc(storyId));
    }

    @Transactional
    public Prepared prepare(Long jobId) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        StoryScene scene = job.getScene();
        Story story = job.getStory();
        List<Cast> cast = Stream.concat(Stream.of(story.getPrimaryCharacter()),
                        storyCharacters.findByStoryIdOrderByIdAsc(story.getId()).stream().map(StoryCharacter::getCharacter))
                .limit(MAX_REFERENCES)
                .map(c -> new Cast(c.getId(), c.getVisualAnchorTokens(), characterSeed(c), c.getReferenceImageUrl()))
                .toList();
        // Aynı masalın sahneleri aynı tohumdan türetilir: ışık ve renk dünyası sahneler arasında yakın kalır.
        long seed = Math.floorMod(story.getId() * 7919L + scene.getSceneNumber(), 2_000_000_000L);
        return new Prepared(jobId, scene.getId(), story.getId(), scene.getSceneNumber(), scene.getImagePrompt(), seed, cast);
    }

    /** İşlem dışında: eksik karakter portreleri ve sahne resmi üretilir. Hata fırlatırsa iş yeniden denenir. */
    public Generated generate(Prepared prepared) {
        AtomicReference<String> provider = new AtomicReference<>();
        Map<Long, String> created = new LinkedHashMap<>();
        List<byte[]> references = new ArrayList<>();
        StringBuilder guide = new StringBuilder();
        if (useReferences) {
            try {
                for (Cast c : prepared.cast()) {
                    byte[] portrait;
                    if (c.referenceKey() != null && storage.load(c.referenceKey()).isPresent()) {
                        portrait = read(storage.load(c.referenceKey()).get());
                    } else {
                        portrait = call(new ImageClient.ImageRequest(portraitPrompt(c.anchors()), 768, 768, c.seed(), List.of()), provider).image();
                        String key = "characters/" + c.characterId() + "/reference-" + shortId() + ".jpg";
                        storage.store(key, Images.fitJpeg(portrait, 768));
                        created.put(c.characterId(), key);
                    }
                    references.add(Images.fitJpeg(portrait, REFERENCE_SIDE));
                    guide.append(guide.isEmpty() ? "" : "; ").append("image ").append(references.size() - 1).append(" shows ").append(c.anchors());
                }
            } catch (RuntimeException e) {
                created.values().forEach(storage::delete);
                throw e;
            }
        }
        String prompt = references.isEmpty() ? prepared.prompt()
                : "Keep every character exactly as drawn in the reference images (" + guide + "). " + prepared.prompt();
        ImageClient.ImageResult image;
        try {
            image = call(new ImageClient.ImageRequest(prompt, width, height, prepared.seed(), references), provider);
        } catch (RuntimeException e) {
            // Üretilen portreler kaybolmasın: sonraki denemede yeniden kullanılmak üzere kaydedilir.
            if (!created.isEmpty()) {
                applyReferences(created);
            }
            throw e;
        }
        return new Generated(image, provider.get(), created);
    }

    @Transactional
    public void complete(Prepared prepared, Generated generated) {
        GenerationJob job = jobs.findById(prepared.jobId()).orElseThrow();
        StoryScene scene = scenes.findById(prepared.sceneId()).orElseThrow();
        String extension = switch (generated.image().mimeType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String key = "stories/" + prepared.storyId() + "/scene-" + prepared.sceneNumber() + "-" + shortId() + extension;
        storage.store(key, generated.image().image());
        String old = scene.getImageUrl();
        scene.setImageUrl(key);
        scene.setGenerationSeed(prepared.seed());
        if (prepared.sceneNumber() == 1) {
            scene.getStory().setCoverImageUrl(key);
        }
        if (old != null) {
            storage.delete(old);
        }
        applyReferences(generated.newReferences());
        job.setStatus(GenerationJob.Status.SUCCEEDED);
        job.setProviderName(generated.provider());
        job.setFinishedAt(Instant.now());
        job.setLastError(null);
    }

    @Transactional
    public void fail(Long jobId, Exception error) {
        GenerationJob job = jobs.findById(jobId).orElseThrow();
        if (JobRetry.reschedule(job, error, 30)) {
            log.info("Sahne resmi yeniden denenecek (job={}, deneme={}): {}", jobId, job.getAttemptCount(), job.getLastError());
        } else {
            log.warn("Sahne resmi hazırlanamadı (job={}, story={}): {}", jobId, job.getStory().getId(), job.getLastError());
        }
    }

    /** Masal sayfası ve API için resim durumu. */
    @Transactional(readOnly = true)
    public StoryDetailResponse.Progress status(Long storyId, List<StoryScene> list) {
        return MediaProgress.of(latestJobs(storyId), list, s -> s.getImageUrl() != null);
    }

    /** Sahiplik denetimiyle sahne resmi. Başka ebeveynin masalı için NotFound. */
    @Transactional(readOnly = true)
    public ImageFile file(Long parentId, Long storyId, int sceneNumber) {
        stories.findByIdAndParentId(storyId, parentId).orElseThrow(() -> new NotFoundException("Masal bulunamadı"));
        StoryScene scene = scenes.findByStoryIdOrderBySceneNumberAsc(storyId).stream()
                .filter(s -> s.getSceneNumber() == sceneNumber && s.getImageUrl() != null)
                .findFirst().orElseThrow(() -> new NotFoundException("Resim bulunamadı"));
        Resource resource = storage.load(scene.getImageUrl()).orElseThrow(() -> new NotFoundException("Resim bulunamadı"));
        String key = scene.getImageUrl();
        return new ImageFile(resource, key.endsWith(".png") ? "image/png" : key.endsWith(".webp") ? "image/webp" : "image/jpeg");
    }

    /**
     * Karakter portresi yalnızca boşsa yazılır; aynı anda iki iş üretmişse fazlası silinir.
     * İşlem dışından da çağrıldığı için kayıt açıkça yapılır.
     */
    private void applyReferences(Map<Long, String> created) {
        created.forEach((characterId, key) -> {
            CharacterBible c = characters.findById(characterId).orElse(null);
            if (c == null || c.getReferenceImageUrl() != null) {
                storage.delete(key);
                return;
            }
            c.setReferenceImageUrl(key);
            if (c.getBaseSeed() == null) {
                c.setBaseSeed(characterSeed(c));
            }
            characters.save(c);
        });
    }

    private ImageClient.ImageResult call(ImageClient.ImageRequest request, AtomicReference<String> provider) {
        ImageClient.ImageResult result = router.call(AiProviderConfig.Type.IMAGE, ImageClient.class, (client, config) -> {
            provider.set(config.getProviderName());
            return client.generate(request, config);
        });
        if (result.image() == null || result.image().length == 0) {
            throw new IllegalStateException("Görsel sağlayıcısı boş dosya döndü");
        }
        return result;
    }

    private Map<Long, GenerationJob.Status> latestJobs(Long storyId) {
        Map<Long, GenerationJob.Status> latest = new HashMap<>();
        for (GenerationJob job : jobs.findByStoryIdAndJobTypeOrderByIdAsc(storyId, GenerationJob.Type.SCENE_IMAGE)) {
            if (job.getScene() != null) {
                latest.put(job.getScene().getId(), job.getStatus());
            }
        }
        return latest;
    }

    static String portraitPrompt(String anchors) {
        return "Character reference portrait for a children's picture book: " + anchors
                + ". Full body, standing, friendly smile, facing the viewer, centered, plain white background, no other characters. Style: "
                + StoryPromptBuilder.STYLE;
    }

    private static long characterSeed(CharacterBible c) {
        return c.getBaseSeed() != null ? c.getBaseSeed() : Math.floorMod(c.getId() * 104_729L, 2_000_000_000L);
    }

    private static byte[] read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
