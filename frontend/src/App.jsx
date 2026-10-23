import React, { useState, useEffect } from 'react';
import ParentAuth from './components/ParentAuth';
import AdminAuth from './components/AdminAuth';
import UserDashboard from './components/UserDashboard';
import ParentPortal from './components/parent/ParentPortal';
import './App.css';

export default function App() {
  const [portal, setPortal] = useState('parent'); // 'parent' veya 'admin'
  const [currentUser, setCurrentUser] = useState(null);
  const backendUrl = 'http://localhost:8080';

  // URL hash kontrolü (örn. #admin veya #parent)
  useEffect(() => {
    const handleHash = () => {
      const hash = window.location.hash.toLowerCase();
      if (hash.includes('admin')) {
        setPortal('admin');
      } else if (hash.includes('parent')) {
        setPortal('parent');
      }
    };

    handleHash();
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, []);

  const handlePortalChange = (newPortal) => {
    setPortal(newPortal);
    window.location.hash = newPortal;
  };

  const handleLoginSuccess = (userData) => {
    setCurrentUser(userData);
  };

  const handleLogout = () => {
    setCurrentUser(null);
    window.location.hash = '';
  };

  // EBEVEYN GİRİŞ YAPTIYSA DOĞRUDAN EBEVEYN PORTALI DÜZENİNİ (NAVBAR + İÇERİK + FOOTER) GÖSTER
  if (currentUser && currentUser.userType === 'PARENT') {
    return (
      <ParentPortal
        user={currentUser}
        onLogout={handleLogout}
        backendUrl={backendUrl}
      />
    );
  }

  // YÖNETİCİ GİRİŞ YAPTIYSA YÖNETİCİ KONSOLU
  if (currentUser && currentUser.userType === 'ADMIN') {
    return (
      <div className="app-container admin-mode">
        <header className="app-header">
          <div className="brand-badge">
            <div className="brand-icon">🛡️</div>
            <div>
              <div className="brand-title">Masal Dünyası AI</div>
              <div className="brand-subtitle">Yönetim ve Güvenlik Konsolu</div>
            </div>
          </div>
          <button type="button" className="navbar-logout-btn" onClick={handleLogout}>
            Yönetici Çıkışı 🚪
          </button>
        </header>
        <main className="content-area">
          <UserDashboard user={currentUser} onLogout={handleLogout} />
        </main>
      </div>
    );
  }

  // GİRİŞ YAPILMADIYSA GİRİŞ / KAYIT PORTALI
  return (
    <div className={`app-container ${portal}-mode`}>
      <header className="app-header">
        <a href="#" className="brand-badge" onClick={() => handlePortalChange('parent')}>
          <div className="brand-icon">
            {portal === 'parent' ? '✨' : '🛡️'}
          </div>
          <div>
            <div className="brand-title">Masal Dünyası AI</div>
            <div className="brand-subtitle">
              {portal === 'parent' ? 'Ebeveyn Masal Platformu' : 'Yönetim ve Güvenlik Portalı'}
            </div>
          </div>
        </a>

        <div className="portal-switch">
          <button
            type="button"
            className={`portal-btn ${portal === 'parent' ? 'active' : ''}`}
            onClick={() => handlePortalChange('parent')}
          >
            👶 Ebeveyn Girişi
          </button>
          <button
            type="button"
            className={`portal-btn ${portal === 'admin' ? 'active' : ''}`}
            onClick={() => handlePortalChange('admin')}
          >
            🛡️ Yönetici Girişi
          </button>
        </div>
      </header>

      <main className="content-area">
        {portal === 'parent' ? (
          <ParentAuth onLoginSuccess={handleLoginSuccess} backendUrl={backendUrl} />
        ) : (
          <AdminAuth onLoginSuccess={handleLoginSuccess} backendUrl={backendUrl} />
        )}
      </main>
    </div>
  );
}
