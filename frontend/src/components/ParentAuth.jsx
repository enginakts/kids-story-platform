import React, { useState } from 'react';

export default function ParentAuth({ onLoginSuccess, backendUrl }) {
  const [tab, setTab] = useState('login'); // 'login' veya 'register'
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Giriş Formu Durumu
  const [loginForm, setLoginForm] = useState({
    email: '',
    password: ''
  });

  // Kayıt Formu Durumu
  const [registerForm, setRegisterForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    password: '',
    preferredLanguage: 'tr'
  });

  // Hızlı test verisi doldurma
  const fillSampleParent = (type) => {
    setErrorMessage('');
    setSuccessMessage('');
    if (type === 'mehmet') {
      setLoginForm({
        email: 'mehmet.kaya@example.com',
        password: 'Parent123!'
      });
      setTab('login');
    } else if (type === 'elif') {
      setLoginForm({
        email: 'elif.demir@example.com',
        password: 'Parent123!'
      });
      setTab('login');
    }
  };

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(`${backendUrl}/api/auth/parent/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(loginForm)
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || 'Giriş yapılamadı. Lütfen bilgilerinizi kontrol edin.');
      }

      setSuccessMessage('Giriş başarılı! Masal dünyasına yönlendiriliyorsunuz...');
      setTimeout(() => {
        onLoginSuccess(data);
      }, 600);
    } catch (err) {
      // Backend kapalıysa veya hata döndüyse kullanıcı dostu uyarı
      setErrorMessage(err.message || 'Sunucuya bağlanılamadı.');
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const response = await fetch(`${backendUrl}/api/auth/parent/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registerForm)
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || 'Kayıt işlemi başarısız.');
      }

      setSuccessMessage('Hesabınız başarıyla oluşturuldu! Hoş geldiniz kredileriniz tanımlandı.');
      setTimeout(() => {
        onLoginSuccess(data);
      }, 700);
    } catch (err) {
      setErrorMessage(err.message || 'Kayıt sırasında bir hata oluştu.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-card parent-card">
      {/* Kart Başlığı */}
      <div className="card-header">
        <div className="badge-info">✨ 3-6 Yaş Yapay Zekâ Masal Evreni</div>
        <h2 className="card-title">Ebeveyn Portalı</h2>
        <p className="card-desc">
          {tab === 'login'
            ? 'Çocuğunuzun hayal dünyasına katılmak ve masallarını yönetmek için giriş yapın.'
            : 'Hemen ücretsiz hesap oluşturun; 3 özel masal ve 1 etkileşimli macera hediyesi kazanın!'}
        </p>
      </div>

      {/* Giriş / Kayıt Geçiş Sekmeleri */}
      <div className="auth-tabs">
        <button
          type="button"
          className={`auth-tab-btn ${tab === 'login' ? 'active' : ''}`}
          onClick={() => { setTab('login'); setErrorMessage(''); }}
        >
          🔑 Giriş Yap
        </button>
        <button
          type="button"
          className={`auth-tab-btn ${tab === 'register' ? 'active' : ''}`}
          onClick={() => { setTab('register'); setErrorMessage(''); }}
        >
          ✨ Yeni Kayıt Ol
        </button>
      </div>

      {/* Bildirim Mesajları */}
      {errorMessage && (
        <div className="alert-box alert-error">
          <span>⚠️</span>
          <span>{errorMessage}</span>
        </div>
      )}
      {successMessage && (
        <div className="alert-box alert-success">
          <span>🎉</span>
          <span>{successMessage}</span>
        </div>
      )}

      {/* GİRİŞ FORMU */}
      {tab === 'login' && (
        <form onSubmit={handleLoginSubmit}>
          <div className="form-group">
            <label className="form-label">E-Posta Adresi</label>
            <div className="input-wrapper">
              <span className="input-icon">✉️</span>
              <input
                type="email"
                required
                className="form-input"
                placeholder="ornek@eposta.com"
                value={loginForm.email}
                onChange={(e) => setLoginForm({ ...loginForm, email: e.target.value })}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">Şifre</label>
            <div className="input-wrapper">
              <span className="input-icon">🔒</span>
              <input
                type="password"
                required
                className="form-input"
                placeholder="••••••••"
                value={loginForm.password}
                onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
              />
            </div>
          </div>

          <button type="submit" className="submit-btn" disabled={loading}>
            {loading ? 'Giriş Yapılıyor...' : 'Masal Dünyasına Giriş Yap →'}
          </button>
        </form>
      )}

      {/* KAYIT FORMU */}
      {tab === 'register' && (
        <form onSubmit={handleRegisterSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Adınız</label>
              <div className="input-wrapper">
                <span className="input-icon">👤</span>
                <input
                  type="text"
                  required
                  className="form-input"
                  placeholder="Ahmet"
                  value={registerForm.firstName}
                  onChange={(e) => setRegisterForm({ ...registerForm, firstName: e.target.value })}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Soyadınız</label>
              <div className="input-wrapper">
                <span className="input-icon">👤</span>
                <input
                  type="text"
                  required
                  className="form-input"
                  placeholder="Yılmaz"
                  value={registerForm.lastName}
                  onChange={(e) => setRegisterForm({ ...registerForm, lastName: e.target.value })}
                />
              </div>
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">E-Posta Adresi</label>
            <div className="input-wrapper">
              <span className="input-icon">✉️</span>
              <input
                type="email"
                required
                className="form-input"
                placeholder="ahmet.yilmaz@example.com"
                value={registerForm.email}
                onChange={(e) => setRegisterForm({ ...registerForm, email: e.target.value })}
              />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label className="form-label">Telefon (İsteğe bağlı)</label>
              <div className="input-wrapper">
                <span className="input-icon">📱</span>
                <input
                  type="tel"
                  className="form-input"
                  placeholder="+90 5XX XXX XX XX"
                  value={registerForm.phoneNumber}
                  onChange={(e) => setRegisterForm({ ...registerForm, phoneNumber: e.target.value })}
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Şifre Belirleyin</label>
              <div className="input-wrapper">
                <span className="input-icon">🔒</span>
                <input
                  type="password"
                  required
                  minLength={6}
                  className="form-input"
                  placeholder="En az 6 karakter"
                  value={registerForm.password}
                  onChange={(e) => setRegisterForm({ ...registerForm, password: e.target.value })}
                />
              </div>
            </div>
          </div>

          <button type="submit" className="submit-btn" disabled={loading}>
            {loading ? 'Hesap Oluşturuluyor...' : 'Ücretsiz Kayıt Ol & Masal Başlat 🎁'}
          </button>
        </form>
      )}

      {/* Hızlı Test Verisi Doldurma Butonları */}
      <div className="quick-fill-box">
        <div className="quick-fill-title">⚡ Test İçin Otomatik Doldur</div>
        <div className="quick-btn-group">
          <button
            type="button"
            className="quick-btn"
            onClick={() => fillSampleParent('mehmet')}
          >
            👨 Mehmet Kaya (2 Çocuklu, VIP)
          </button>
          <button
            type="button"
            className="quick-btn"
            onClick={() => fillSampleParent('elif')}
          >
            👩 Elif Demir (1 Çocuklu, Standart)
          </button>
        </div>
      </div>
    </div>
  );
}
