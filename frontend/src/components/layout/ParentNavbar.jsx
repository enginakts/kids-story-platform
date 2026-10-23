import React from 'react';

export default function ParentNavbar({ user, activeTab, onTabChange, onLogout }) {
  const navItems = [
    { id: 'children', label: 'Çocuklarım', icon: '👶' },
    { id: 'stories', label: 'Masal Kitaplığı', icon: '📖' },
    { id: 'bibles', label: 'Karakter & Mekân', icon: '🎨' },
    { id: 'wallet', label: 'Cüzdan & Paketler', icon: '💳' },
  ];

  return (
    <nav className="parent-navbar">
      <div className="navbar-left">
        <div className="navbar-brand">
          <div className="navbar-logo-icon">✨</div>
          <div className="navbar-brand-texts">
            <span className="navbar-brand-name">Masal Dünyası AI</span>
            <span className="navbar-brand-sub">Ebeveyn Portalı</span>
          </div>
        </div>

        {/* Ana Navigasyon Sekmeleri */}
        <div className="nav-links-wrapper">
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`nav-link-btn ${activeTab === item.id ? 'active' : ''}`}
              onClick={() => onTabChange(item.id)}
            >
              <span className="nav-item-icon">{item.icon}</span>
              <span className="nav-item-text">{item.label}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="navbar-right">
        {/* Kredi Cüzdanı Göstergesi */}
        <div className="credit-badges-group">
          <div className="credit-pill story-pill" title="Kalan Masal Üretim Kredisi">
            <span className="pill-icon">📚</span>
            <span className="pill-value">{user.storyCredits ?? 0}</span>
            <span className="pill-label">Masal</span>
          </div>

          <div className="credit-pill interactive-pill" title="Kalan Etkileşimli Macera Kredisi">
            <span className="pill-icon">🎮</span>
            <span className="pill-value">{user.interactiveCredits ?? 0}</span>
            <span className="pill-label">Etkileşimli</span>
          </div>
        </div>

        {/* Ebeveyn Profil & Çıkış Kartı */}
        <div className="user-profile-menu">
          <img
            src={`https://api.dicebear.com/7.x/bottts/svg?seed=${user.fullName || user.email}`}
            alt="Avatar"
            className="user-nav-avatar"
          />
          <div className="user-nav-details">
            <span className="user-nav-name">{user.fullName || user.email}</span>
            <span className="user-nav-role">Ebeveyn</span>
          </div>
          <button
            type="button"
            className="navbar-logout-btn"
            onClick={onLogout}
            title="Oturumu Kapat"
          >
            Çıkış Yap 🚪
          </button>
        </div>
      </div>
    </nav>
  );
}
