import React, { useState } from 'react';

export default function AdminAuth({ onLoginSuccess, backendUrl }) {
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const [form, setForm] = useState({
    usernameOrEmail: '',
    password: ''
  });

  const fillSampleAdmin = (role) => {
    setErrorMessage('');
    setSuccessMessage('');
    if (role === 'superadmin') {
      setForm({
        usernameOrEmail: 'superadmin',
        password: 'Admin123!'
      });
    } else if (role === 'moderator') {
      setForm({
        usernameOrEmail: 'moderator_ayse',
        password: 'Admin123!'
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(`${backendUrl}/api/auth/admin/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form)
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || 'Yönetici kimliği doğrulanamadı.');
      }

      setSuccessMessage('Yönetici yetkisi doğrulandı. Güvenli panele aktarılıyorsunuz...');
      setTimeout(() => {
        onLoginSuccess(data);
      }, 600);
    } catch (err) {
      setErrorMessage(err.message || 'Yönetici sunucusuna erişilemedi.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-card admin-card">
      <div className="card-header">
        <div className="badge-info">🛡️ YÜKSEK GÜVENLİKLİ SİSTEM ERİŞİMİ</div>
        <h2 className="card-title">Yönetici Portalı</h2>
        <p className="card-desc">
          Platform yönetimi, pedagojik içerik denetimi, prompt şablonları ve sistem izleme konsolu.
        </p>
      </div>

      {errorMessage && (
        <div className="alert-box alert-error">
          <span>🛑</span>
          <span>{errorMessage}</span>
        </div>
      )}
      {successMessage && (
        <div className="alert-box alert-success">
          <span>✅</span>
          <span>{successMessage}</span>
        </div>
      )}

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label className="form-label">Yönetici Kullanıcı Adı veya E-Posta</label>
          <div className="input-wrapper">
            <span className="input-icon">👤</span>
            <input
              type="text"
              required
              className="form-input"
              placeholder="superadmin veya admin@kidstory.com"
              value={form.usernameOrEmail}
              onChange={(e) => setForm({ ...form, usernameOrEmail: e.target.value })}
            />
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Yönetici Güvenlik Parolası</label>
          <div className="input-wrapper">
            <span className="input-icon">🛡️</span>
            <input
              type="password"
              required
              className="form-input"
              placeholder="••••••••••••"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </div>
        </div>

        <button type="submit" className="submit-btn" disabled={loading}>
          {loading ? 'YETKİ SORGULANIYOR...' : 'YÖNETİM KONSOLUNA GİRİŞ YAP →'}
        </button>
      </form>

      {/* Hızlı Test Yönetici Doldurma Butonları */}
      <div className="quick-fill-box">
        <div className="quick-fill-title">⚡ Test Yönetici Hesapları</div>
        <div className="quick-btn-group">
          <button
            type="button"
            className="quick-btn"
            onClick={() => fillSampleAdmin('superadmin')}
          >
            👑 Süper Yönetici (Engin Aktaş)
          </button>
          <button
            type="button"
            className="quick-btn"
            onClick={() => fillSampleAdmin('moderator')}
          >
            🛡️ İçerik Moderatörü (Ayşe Yılmaz)
          </button>
        </div>
      </div>
    </div>
  );
}
