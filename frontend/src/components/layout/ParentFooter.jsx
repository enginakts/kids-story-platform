import React from 'react';

export default function ParentFooter() {
  return (
    <footer className="parent-footer">
      <div className="footer-content">
        <div className="footer-left">
          <span className="footer-badge">🛡️ Pedagojik Güvenlik Standartlı</span>
          <p className="footer-text">
            <strong>Masal Dünyası AI © 2026</strong> — 3-6 yaş çocuklar için güvenli, değerler eğitimini destekleyen ve ebeveyn denetimli yapay zekâ masal ekosistemi.
          </p>
        </div>
        <div className="footer-right">
          <span className="footer-note">Veri İzolasyonu & Ebeveyn Kilidi Aktif</span>
          <div className="footer-links">
            <a href="#children" className="footer-link">Çocuk Profilleri</a>
            <span className="footer-sep">•</span>
            <a href="#privacy" className="footer-link">Gizlilik İlkeleri</a>
            <span className="footer-sep">•</span>
            <a href="#help" className="footer-link">Yardım & Destek</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
