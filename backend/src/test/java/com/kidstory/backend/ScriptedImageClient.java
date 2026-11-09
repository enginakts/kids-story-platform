package com.kidstory.backend;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.service.ai.CloudflareImageClient;
import com.kidstory.backend.service.ai.ImageClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Testlerde Cloudflare görsel sağlayıcısının yerine geçer; istekleri kaydeder, istenirse hata verir. */
public class ScriptedImageClient implements ImageClient {

    public final List<ImageRequest> requests = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;
    private volatile int succeedFirst;

    public void failWith(RuntimeException e) {
        failWith(e, 0);
    }

    /** İlk succeedFirst çağrı başarılı olur, sonrakiler hata verir. */
    public void failWith(RuntimeException e, int succeedFirst) {
        failure = e;
        this.succeedFirst = succeedFirst;
    }

    public void reset() {
        requests.clear();
        failure = null;
    }

    @Override
    public String providerName() {
        return CloudflareImageClient.NAME;
    }

    @Override
    public AiProviderConfig.Type type() {
        return AiProviderConfig.Type.IMAGE;
    }

    @Override
    public ImageResult generate(ImageRequest request, AiProviderConfig config) {
        requests.add(request);
        if (failure != null && requests.size() > succeedFirst) {
            throw failure;
        }
        return new ImageResult(jpeg(request.width(), request.height()), "image/jpeg");
    }

    static byte[] jpeg(int w, int h) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        ScriptedImageClient scriptedImages() {
            return new ScriptedImageClient();
        }
    }
}
