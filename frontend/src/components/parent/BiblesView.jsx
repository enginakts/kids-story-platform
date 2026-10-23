import React from 'react';

export default function BiblesView() {
  const sampleCharacters = [
    { id: 1, name: 'Sevimli Tavşan Pamuk', species: 'Tavşan', traits: 'Meraklı, yardımsever, havuç seven', icon: '🐰' },
    { id: 2, name: 'Cesur Yavru Leo', species: 'Aslan', traits: 'Cesur, maceracı, keşif meraklısı', icon: '🦁' },
  ];

  const sampleLocations = [
    { id: 1, name: 'Büyülü Fısıltı Ormanı', env: 'Doğa / Orman', lighting: 'Güneş ışığı hüzmeleri, masalsı parıltılar', icon: '🌲' },
    { id: 2, name: 'Pamuk Bulutlar Ülkesi', env: 'Gökyüzü', lighting: 'Pastel pembe ve gün batımı altın tonları', icon: '☁️' },
  ];

  return (
    <div className="section-placeholder-view">
      <div className="placeholder-icon">🎨</div>
      <h2 className="placeholder-title">Karakter ve Mekân Tutarlılık Rehberi (Bibles)</h2>
      <p className="placeholder-desc">
        Masal sahneleri boyunca kahramanınızın ve arka planın görsel olarak sabit kalmasını sağlayan şablon kütüphanesi.
      </p>

      <div style={{ marginTop: '2rem', textAlign: 'left', width: '100%' }}>
        <h3 style={{ fontSize: '1.2rem', marginBottom: '1rem', color: '#1e1b4b' }}>
          🐾 Hazır Kahraman Şablonları (Character Bibles)
        </h3>
        <div className="children-grid" style={{ marginBottom: '2.5rem' }}>
          {sampleCharacters.map((c) => (
            <div key={c.id} className="child-profile-card">
              <div className="child-card-top">
                <div className="brand-icon" style={{ background: '#ede9fe', color: '#7c3aed' }}>{c.icon}</div>
                <div className="child-primary-info">
                  <h4 style={{ fontSize: '1.1rem', fontWeight: 800 }}>{c.name}</h4>
                  <span className="child-gender-tag">{c.species}</span>
                </div>
              </div>
              <p style={{ fontSize: '0.88rem', color: '#64748b', marginTop: '0.5rem' }}>{c.traits}</p>
            </div>
          ))}
        </div>

        <h3 style={{ fontSize: '1.2rem', marginBottom: '1rem', color: '#1e1b4b' }}>
          🌲 Hazır Mekân Şablonları (Location Bibles)
        </h3>
        <div className="children-grid">
          {sampleLocations.map((l) => (
            <div key={l.id} className="child-profile-card">
              <div className="child-card-top">
                <div className="brand-icon" style={{ background: '#ecfdf5', color: '#059669' }}>{l.icon}</div>
                <div className="child-primary-info">
                  <h4 style={{ fontSize: '1.1rem', fontWeight: 800 }}>{l.name}</h4>
                  <span className="child-gender-tag">{l.env}</span>
                </div>
              </div>
              <p style={{ fontSize: '0.88rem', color: '#64748b', marginTop: '0.5rem' }}>{l.lighting}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
