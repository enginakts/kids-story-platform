import React, { useState, useEffect } from 'react';

export default function ChildrenView({ user, backendUrl, onStartStory }) {
  const [children, setChildren] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [notification, setNotification] = useState('');

  // Yeni Çocuk Formu
  const [newChild, setNewChild] = useState({
    name: '',
    age: 4,
    gender: 'Kız',
    favoriteAnimals: 'Tavşan, Kedi',
    favoriteColors: 'Pembe, Mavi',
    specialInterests: 'Uzay maceraları, resim yapma',
    bedtimeHour: '20:30:00'
  });

  const fetchChildren = async () => {
    try {
      setLoading(true);
      const res = await fetch(`${backendUrl}/api/children?parentId=${user.id}`);
      if (res.ok) {
        const data = await res.json();
        setChildren(data);
      }
    } catch (err) {
      console.error('Çocuk profilleri yüklenirken hata:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChildren();
  }, [user.id]);

  const handleAddChildSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setNotification('');

    try {
      const res = await fetch(`${backendUrl}/api/children?parentId=${user.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newChild)
      });

      if (res.ok) {
        const created = await res.json();
        setChildren([...children, created]);
        setShowAddModal(false);
        setNotification(`🎉 ${created.name} başarıyla profil olarak eklendi!`);
        setTimeout(() => setNotification(''), 4000);
        // Formu sıfırla
        setNewChild({
          name: '',
          age: 4,
          gender: 'Kız',
          favoriteAnimals: '',
          favoriteColors: '',
          specialInterests: '',
          bedtimeHour: '20:30:00'
        });
      }
    } catch (err) {
      console.error('Çocuk eklenemedi:', err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="children-view-container">
      {/* Üst Bilgi Başlığı ve Ekle Butonu */}
      <div className="view-header-row">
        <div>
          <h1 className="view-main-title">👶 Çocuk Profillerim</h1>
          <p className="view-sub-title">
            Her çocuğunuz için özel ilgi alanları, yaş grubu ve görsel tercihler tanımlayarak masal deneyimini kişiselleştirin.
          </p>
        </div>

        <button
          type="button"
          className="add-child-action-btn"
          onClick={() => setShowAddModal(true)}
        >
          ➕ Yeni Çocuk Profili Ekle
        </button>
      </div>

      {notification && (
        <div className="alert-box alert-success" style={{ marginBottom: '1.5rem' }}>
          <span>{notification}</span>
        </div>
      )}

      {/* Yükleniyor Durumu */}
      {loading ? (
        <div className="loading-state-card">
          <div className="loading-spinner">✨</div>
          <p>Çocuk profilleri yükleniyor...</p>
        </div>
      ) : children.length === 0 ? (
        <div className="empty-children-card">
          <div className="empty-icon">🎈</div>
          <h3>Henüz çocuk profili eklenmemiş</h3>
          <p>İlk çocuğunuzu ekleyerek onun yaşına ve hayal dünyasına özel masallar üretmeye başlayın.</p>
          <button
            type="button"
            className="add-child-action-btn"
            style={{ marginTop: '1rem' }}
            onClick={() => setShowAddModal(true)}
          >
            ➕ Hemen İlk Çocuğu Ekle
          </button>
        </div>
      ) : (
        /* Çocuk Kartları Izgarası */
        <div className="children-grid">
          {children.map((child) => (
            <div key={child.id} className="child-profile-card">
              <div className="child-card-top">
                <img
                  src={child.avatarUrl || `https://api.dicebear.com/7.x/bottts/svg?seed=${child.name}`}
                  alt={child.name}
                  className="child-card-avatar"
                />
                <div className="child-primary-info">
                  <div className="child-name-row">
                    <h3 className="child-card-name">{child.name}</h3>
                    <span className="child-age-pill">{child.age} Yaşında</span>
                  </div>
                  <span className="child-gender-tag">{child.gender}</span>
                </div>
              </div>

              <div className="child-details-list">
                <div className="child-detail-item">
                  <span className="detail-icon">🐾</span>
                  <span className="detail-label">Sevdiği Hayvanlar:</span>
                  <span className="detail-value">{child.favoriteAnimals || 'Belirtilmedi'}</span>
                </div>

                <div className="child-detail-item">
                  <span className="detail-icon">🎨</span>
                  <span className="detail-label">Sevdiği Renkler:</span>
                  <span className="detail-value">{child.favoriteColors || 'Belirtilmedi'}</span>
                </div>

                <div className="child-detail-item">
                  <span className="detail-icon">🚀</span>
                  <span className="detail-label">Özel İlgi Alanları:</span>
                  <span className="detail-value">{child.specialInterests || 'Belirtilmedi'}</span>
                </div>

                <div className="child-detail-item">
                  <span className="detail-icon">🌙</span>
                  <span className="detail-label">Uyku Saati:</span>
                  <span className="detail-value">{child.bedtimeHour || '20:30'}</span>
                </div>
              </div>

              <div className="child-card-actions">
                <button
                  type="button"
                  className="start-story-btn"
                  onClick={() => onStartStory(child)}
                >
                  ✨ {child.name} İçin Masal Üret →
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* YENİ ÇOCUK EKLEME MODALI */}
      {showAddModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h2>➕ Yeni Çocuk Profili Tanımla</h2>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowAddModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleAddChildSubmit}>
              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Çocuğun Adı</label>
                  <input
                    type="text"
                    required
                    className="form-input"
                    placeholder="Örn: Zeynep"
                    value={newChild.name}
                    onChange={(e) => setNewChild({ ...newChild, name: e.target.value })}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Yaşı ({newChild.age} Yaş)</label>
                  <input
                    type="range"
                    min="2"
                    max="8"
                    step="1"
                    className="form-range"
                    value={newChild.age}
                    onChange={(e) => setNewChild({ ...newChild, age: parseInt(e.target.value) })}
                  />
                  <div className="range-hints">
                    <span>2 Yaş</span>
                    <span>4 Yaş</span>
                    <span>6 Yaş</span>
                    <span>8 Yaş</span>
                  </div>
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">Cinsiyet</label>
                  <select
                    className="form-input"
                    value={newChild.gender}
                    onChange={(e) => setNewChild({ ...newChild, gender: e.target.value })}
                  >
                    <option value="Kız">Kız</option>
                    <option value="Erkek">Erkek</option>
                    <option value="Belirtmek İstemiyorum">Belirtmek İstemiyorum</option>
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Alışılmış Uyku Saati</label>
                  <input
                    type="time"
                    className="form-input"
                    value={newChild.bedtimeHour.substring(0, 5)}
                    onChange={(e) => setNewChild({ ...newChild, bedtimeHour: e.target.value + ':00' })}
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">🐾 En Sevdiği Hayvanlar</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Örn: Tavşan, Yavru Köpek, Dinozor"
                  value={newChild.favoriteAnimals}
                  onChange={(e) => setNewChild({ ...newChild, favoriteAnimals: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">🎨 En Sevdiği Renkler</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Örn: Sarı, Turkuaz, Pembe"
                  value={newChild.favoriteColors}
                  onChange={(e) => setNewChild({ ...newChild, favoriteColors: e.target.value })}
                />
              </div>

              <div className="form-group">
                <label className="form-label">🚀 Özel İlgi Alanları ve Merakları</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Örn: Uzay araçları, denizaltılar, sihirli ormanlar"
                  value={newChild.specialInterests}
                  onChange={(e) => setNewChild({ ...newChild, specialInterests: e.target.value })}
                />
              </div>

              <div className="modal-actions-row">
                <button
                  type="button"
                  className="modal-cancel-btn"
                  onClick={() => setShowAddModal(false)}
                >
                  Vazgeç
                </button>
                <button
                  type="submit"
                  className="modal-submit-btn"
                  disabled={submitting}
                >
                  {submitting ? 'Kaydediliyor...' : 'Profili Kaydet ✅'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
