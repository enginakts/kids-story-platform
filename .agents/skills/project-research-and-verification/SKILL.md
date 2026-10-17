---
name: project-research-and-verification
description: >-
  Use this skill whenever answering user queries, proposing architectures, tools, libraries, AI models, prompts, pedagogical guidelines, or technical implementations for the 3-6 age AI story platform. Instructs the agent to conduct web research first, cross-reference industry best practices, and verify strict project suitability before responding.
---

# Proje Araştırma ve Uygunluk Doğrulama Becerisi (Research & Verification Skill)

Bu beceri, 3-6 yaş çocuk masal platformu projesiyle ilgili kullanıcıya verilecek tüm cevapların, mimari önerilerin, kütüphane seçimlerinin ve kod bloklarının **önce web üzerinde araştırılmasını**, doğrulanmasını ve projenin gereksinimlerine uygunluğunun denetlenmesini sağlar.

## Çalışma Akışı ve Adımlar

### 1. İhtiyacın ve Alanın Analizi
Kullanıcı bir soru sorduğunda veya bir özellik talep ettiğinde:
- **Teknik Katman:** LLM JSON çıktısı, TTS kelime zaman damgası (timestamps), FLUX / IP-Adapter karakter tutarlılığı, mobil sayfa çevirme ve karaoke ses senkronizasyonu.
- **Pedagojik ve Güvenlik Katmanı:** 3-6 yaş dil ve bilişsel seviyesi, COPPA / GDPR-K ve Apple/Google Çocuk kategorisi kılavuzları.

### 2. Web Üzerinde Güncel Araştırma (`search_web`)
- Konuyla ilgili resmi API dokümantasyonunu, GitHub repolarını, en güncel SDK sürümlerini ve sektör standartlarını ara.
- Hızla değişen yapay zekâ modellerinin fiyatlandırmasını, latency (gecikme) sürelerini ve API sınırlarını güncel web aramalarıyla teyit et.

### 3. Proje Standartlarına Göre Uygunluk Denetimi
Arama sonuçlarının aşağıdaki temel proje ilkeleriyle uyumunu denetle:
- **4–6 Sahne Kuralı:** 3-6 yaş dikkat süresini aşmayan, her sahnede 1–2 cümlelik akıcı/kafiyeli yapı.
- **Görsel Tutarlılık:** Karakter çapası (Character Anchor) ve yumuşak pastel suluboya illüstrasyon stili.
- **Kelime Senkronizasyonlu TTS:** Ses çalarken aktif kelimeyi parlatabilmek için salise düzeyinde zamanlama desteği.
- **Mutlak Pedagojik Güvenlik:** Sıfır korku/şiddet/ayrılık; pozitif pekiştirme ve sevgi temaları.
- **Ebeveyn Kilidi:** Tüm ayar ve oluşturma aşamalarında rastgele bilişsel soru zorunluluğu.

### 4. Cevabın Hazırlanması ve Sunumu
- Kullanıcıya sunulan yanıt, araştırmanın sonucunda elenen ve seçilen en iyi yaklaşımı açıkça gerekçelendirmelidir.
- Güncel kütüphane, SDK veya API yöntemleri somut kod/yapılandırma örnekleriyle desteklenmelidir.
