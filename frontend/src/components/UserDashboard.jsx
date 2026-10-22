import React from 'react';

export default function UserDashboard({ user, onLogout }) {
  const isParent = user.userType === 'PARENT';

  return (
    <div className={`dashboard-card ${isParent ? 'parent-card' : 'admin-card'}`}>
      <div className="dash-header">
        <div className="dash-avatar">
          {isParent ? '👨‍👩‍👧' : '🛡️'}
        </div>
        <div className="dash-info">
          <h2>{user.fullName || user.username || user.email}</h2>
          <span className="dash-role-badge badge-info">
            {user.role} ({user.userType})
          </span>
        </div>
      </div>

      {/* İstatistikler / Krediler (Ebeveyn ise) */}
      {isParent ? (
        <div className="dash-stats-grid">
          <div className="stat-box">
            <div className="stat-value">{user.storyCredits ?? 0}</div>
            <div className="stat-label">📚 Masal Üretim Kredisi</div>
          </div>
          <div className="stat-box">
            <div className="stat-value">{user.interactiveCredits ?? 0}</div>
            <div className="stat-label">🎮 Etkileşimli Macera Kredisi</div>
          </div>
        </div>
      ) : (
        <div className="dash-stats-grid">
          <div className="stat-box">
            <div className="stat-value">TAM</div>
            <div className="stat-label">🛡️ Sistem Denetim Yetkisi</div>
          </div>
          <div className="stat-box">
            <div className="stat-value">20 Tablo</div>
            <div className="stat-label">📊 Aktif Veritabanı Varlığı</div>
          </div>
        </div>
      )}

      {/* JWT Token Önizleme (Geliştirici / Test Kolaylığı İçin) */}
      <div style={{ marginBottom: '0.5rem', fontSize: '0.82rem', fontWeight: 600 }}>
        🔑 Üretilen JWT Oturum Anahtarı (Bearer Token):
      </div>
      <div className="token-viewer">
        {user.token}
      </div>

      <button
        type="button"
        className="logout-btn"
        onClick={onLogout}
      >
        🚪 Oturumu Kapat ({user.userType === 'ADMIN' ? 'Yönetici Çıkışı' : 'Ebeveyn Çıkışı'})
      </button>
    </div>
  );
}
