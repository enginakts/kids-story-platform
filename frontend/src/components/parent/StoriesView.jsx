import React from 'react';

export default function StoriesView({ onNavigateToChildren }) {
  return (
    <div className="section-placeholder-view">
      <div className="placeholder-icon">📖</div>
      <h2 className="placeholder-title">Masal Kitaplığımız</h2>
      <p className="placeholder-desc">
        Bu alanda çocuğunuz için üretilen 4-6 sahnelik kişiselleştirilmiş masallar, ses kayıtları ve sahne görselleri yer alır.
      </p>
      <div className="placeholder-card">
        <span className="badge-info">✨ Adım 4 & 5 Kapsamı</span>
        <h3>Henüz Üretilmiş Bir Masal Yok</h3>
        <p>Masal motorunu çalıştırmak için bir çocuk profili seçin ve hikâyenizi oluşturun.</p>
        <button
          type="button"
          className="start-story-btn"
          style={{ maxWidth: '280px', margin: '1rem auto 0 auto' }}
          onClick={onNavigateToChildren}
        >
          👶 Çocuğumu Seç ve Masal Başlat
        </button>
      </div>
    </div>
  );
}
