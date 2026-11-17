package com.kidstory.backend.service.ai;

import com.kidstory.backend.entity.AiProviderConfig;
import com.kidstory.backend.repository.AiProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Hibrit ve değiştirilebilir yapay zekâ katmanı: sağlayıcılar yönetimdeki öncelik sırasıyla denenir,
 * hata alınınca sonrakine geçilir. Yeni sağlayıcı için bir adaptör ve bir tablo satırı yeterlidir.
 */
@Service
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final AiProviderConfigRepository configs;
    private final List<AiClient> clients;
    private final UsageRecorder usage;

    /** Kullanım kaydı olmadan (birim testleri). */
    public AiRouter(AiProviderConfigRepository configs, List<AiClient> clients) {
        this(configs, clients, UsageRecorder.NONE);
    }

    /** Her dış çağrı denemesi (yedeğe geçiş ve başarısızlık dahil) kullanım raporuna yazılır (V14). */
    @Autowired
    public AiRouter(AiProviderConfigRepository configs, List<AiClient> clients, UsageRecorder usage) {
        this.configs = configs;
        this.clients = clients;
        this.usage = usage;
    }

    public <C extends AiClient, R> R call(AiProviderConfig.Type type, Class<C> clientType,
                                          BiFunction<C, AiProviderConfig, R> action) {
        RuntimeException last = null;
        for (AiProviderConfig config : configs.findByProviderTypeAndActiveTrueOrderByPriorityAsc(type)) {
            C client = find(clientType, config.getProviderName());
            if (client == null) {
                continue;
            }
            UsageMeter.start();
            long started = System.nanoTime();
            try {
                R result = action.apply(client, config);
                usage.record(config, true, null, elapsedMs(started), UsageMeter.finish(), UsageMeter.currentContext());
                return result;
            } catch (RuntimeException e) {
                usage.record(config, false, e.getMessage(), elapsedMs(started), UsageMeter.finish(), UsageMeter.currentContext());
                log.warn("{} sağlayıcısı başarısız: {}", config.getProviderName(), e.toString());
                last = e;
            }
        }
        throw new IllegalStateException("Kullanılabilir " + type + " sağlayıcısı yok", last);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
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
