package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Hibrit ve değiştirilebilir yapay zekâ katmanı: sağlayıcılar yönetimdeki öncelik sırasıyla denenir,
 * hata alınınca sonrakine geçilir. Yeni sağlayıcı için bir adaptör ve bir tablo satırı yeterlidir.
 */
@Service
@RequiredArgsConstructor
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final AiProviderConfigRepository configs;
    private final List<AiClient> clients;

    /** Sonuç ve onu üreten sağlayıcının adı. */
    public record Outcome<R>(R value, String providerName) {
    }

    public <C extends AiClient, R> R call(AiProviderConfig.Type type, Class<C> clientType,
                                          BiFunction<C, AiProviderConfig, R> action) {
        return invoke(type, clientType, action).value();
    }

    public <C extends AiClient, R> Outcome<R> invoke(AiProviderConfig.Type type, Class<C> clientType,
                                                     BiFunction<C, AiProviderConfig, R> action) {
        RuntimeException last = null;
        for (AiProviderConfig config : configs.findByProviderTypeAndActiveTrueOrderByPriorityAsc(type)) {
            C client = find(clientType, config.getProviderName());
            if (client == null) {
                continue;
            }
            try {
                return new Outcome<>(action.apply(client, config), config.getProviderName());
            } catch (RuntimeException e) {
                log.warn("{} sağlayıcısı başarısız: {}", config.getProviderName(), e.toString());
                last = e;
            }
        }
        throw new IllegalStateException("Kullanılabilir " + type + " sağlayıcısı yok", last);
    }

    private <C extends AiClient> C find(Class<C> clientType, String name) {
        return clients.stream()
                .filter(clientType::isInstance)
                .filter(c -> c.providerName().equals(name))
                .map(clientType::cast)
                .findFirst()
                .orElse(null);
    }
}
