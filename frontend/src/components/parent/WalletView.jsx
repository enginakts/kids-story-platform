import React from 'react';

export default function WalletView({ user }) {
  const plans = [
    {
      code: 'FREE_TIER',
      name: 'Ücretsiz Başlangıç',
      price: '0.00 ₺',
      storyCredits: 3,
      interactiveCredits: 1,
      childLimit: 1,
      retention: '7 Günlük Arşiv',
      current: user.storyCredits <= 3 && user.interactiveCredits <= 1
    },
    {
      code: 'STANDARD_MONTHLY',
      name: 'Standart Masal Paketi',
      price: '149.99 ₺ / ay',
      storyCredits: 15,
      interactiveCredits: 5,
      childLimit: 2,
      retention: '180 Günlük Arşiv',
      current: user.storyCredits > 3
    },
    {
      code: 'FAMILY_VIP',
      name: 'Aile VIP & Sonsuz Masal',
      price: '299.99 ₺ / ay',
      storyCredits: 40,
      interactiveCredits: 15,
      childLimit: 5,
      retention: 'Süresiz Arşiv',
      current: false
    }
  ];

  return (
    <div className="section-placeholder-view">
      <div className="placeholder-icon">💳</div>
      <h2 className="placeholder-title">Kredi Cüzdanı ve Abonelik Yönetimi</h2>
      <p className="placeholder-desc">
        Masal üretimi için kalan kredilerinizi takip edin veya ihtiyacınıza uygun abonelik paketini seçin.
      </p>

      {/* Cüzdan Durum Kartı */}
      <div className="dash-stats-grid" style={{ maxWidth: '600px', margin: '2rem auto' }}>
        <div className="stat-box">
          <div className="stat-value">{user.storyCredits ?? 0}</div>
          <div className="stat-label">📚 Kalan Tek Seferlik Masal Kredisi</div>
        </div>
        <div className="stat-box">
          <div className="stat-value">{user.interactiveCredits ?? 0}</div>
          <div className="stat-label">🎮 Kalan Etkileşimli Macera Kredisi</div>
        </div>
      </div>

      {/* Paket Karşılaştırma Vitrini */}
      <h3 style={{ fontSize: '1.25rem', marginBottom: '1.25rem', textAlign: 'center', color: '#1e1b4b' }}>
        🎁 Abonelik Paketleri (Faz 1)
      </h3>
      <div className="children-grid" style={{ marginTop: '1rem' }}>
        {plans.map((p) => (
          <div key={p.code} className="child-profile-card" style={{ textAlign: 'center' }}>
            <h4 style={{ fontSize: '1.2rem', fontWeight: 800 }}>{p.name}</h4>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#7c3aed', margin: '0.6rem 0' }}>
              {p.price}
            </div>
            <div className="child-details-list" style={{ textAlign: 'left', marginTop: '1rem' }}>
              <div className="child-detail-item">
                <span className="detail-icon">📚</span>
                <span className="detail-label">Masal Kredisi:</span>
                <span className="detail-value">{p.storyCredits} Adet/Ay</span>
              </div>
              <div className="child-detail-item">
                <span className="detail-icon">🎮</span>
                <span className="detail-label">Etkileşimli Kredi:</span>
                <span className="detail-value">{p.interactiveCredits} Adet/Ay</span>
              </div>
              <div className="child-detail-item">
                <span className="detail-icon">👶</span>
                <span className="detail-label">Çocuk Profili:</span>
                <span className="detail-value">Maks {p.childLimit} Çocuk</span>
              </div>
              <div className="child-detail-item">
                <span className="detail-icon">⏳</span>
                <span className="detail-label">Saklama:</span>
                <span className="detail-value">{p.retention}</span>
              </div>
            </div>
            {p.current ? (
              <span className="badge-info" style={{ marginTop: '1rem', display: 'inline-block' }}>
                ✅ Mevcut Paketiniz
              </span>
            ) : (
              <button type="button" className="start-story-btn" style={{ marginTop: '1rem' }}>
                Paketi Seç ✨
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
