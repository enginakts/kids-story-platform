---
name: github-deployment-management
description: >-
  Use this skill whenever managing repository commits, pushes, or sync operations to GitHub. Enforces that changes are NEVER automatically pushed to GitHub without an explicit user command.
---

# GitHub Yükleme ve Senkronizasyon Yönetimi (GitHub Deployment Management)

Bu beceri, projedeki yerel kod, mimari ve doküman değişikliklerinin GitHub'a aktarılmasını katı kurallarla yönetir.

## Katı Kurallar ve İlkeler

1. **Otomatik Yükleme Kesinlikle Yasaktır:**
   - Yapılan hiçbir geliştirme, dosya oluşturma, düzenleme veya silme işlemi kendi kendine (otomatik olarak) GitHub'a push edilmez / yüklenmez.
   - Değişiklikler ilk olarak yerel ortamda (workspace) tamamlanır ve test edilir.

2. **Kullanıcı Açık Talimatı Şartı:**
   - Yalnızca kullanıcı açıkça *"GitHub'a yükle"*, *"commit at"*, *"değişiklikleri push et"* gibi bir talimat verdiğinde GitHub senkronizasyonu başlatılır.
   - Kullanıcı istemediği sürece GitHub'a tek bir dosya dahi yüklenmez.

3. **Yükleme Süreci (Talimat Geldiğinde):**
   - Hangi dosyaların değiştiğini veya yeni eklendiğini listele.
   - Açıklayıcı ve standartlara uygun commit mesajları belirle (`feat:`, `fix:`, `docs:`, `refactor:`).
   - Yüklemeyi gerçekleştir ve kullanıcıya sonuç linkini/özetini bildir.
