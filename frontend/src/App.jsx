import React, { useState, useEffect } from 'react';
import ParentAuth from './components/ParentAuth';
import AdminAuth from './components/AdminAuth';
import UserDashboard from './components/UserDashboard';
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
  };

  return (
    <div className={`app-container ${portal}-mode`}>
      {/* ÜST GEZİNTİ VE PORTAL GEÇİŞ ÇUBUĞU */}
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

        {/* Portal Seçici (Farklı Giriş Kapıları) */}
        {!currentUser && (
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
        )}
      </header>

      {/* ANA İÇERİK ALANI */}
      <main className="content-area">
        {currentUser ? (
          <UserDashboard user={currentUser} onLogout={handleLogout} />
        ) : portal === 'parent' ? (
          <ParentAuth onLoginSuccess={handleLoginSuccess} backendUrl={backendUrl} />
        ) : (
          <AdminAuth onLoginSuccess={handleLoginSuccess} backendUrl={backendUrl} />
        )}
      </main>
    </div>
  );
}
