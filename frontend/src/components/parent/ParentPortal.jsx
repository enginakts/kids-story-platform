import React, { useState } from 'react';
import ParentNavbar from '../layout/ParentNavbar';
import ParentFooter from '../layout/ParentFooter';
import ChildrenView from './ChildrenView';
import StoriesView from './StoriesView';
import BiblesView from './BiblesView';
import WalletView from './WalletView';

export default function ParentPortal({ user, onLogout, backendUrl }) {
  const [activeTab, setActiveTab] = useState('children'); // 'children', 'stories', 'bibles', 'wallet'
  const [selectedChildForStory, setSelectedChildForStory] = useState(null);

  const handleStartStory = (child) => {
    setSelectedChildForStory(child);
    setActiveTab('stories');
  };

  return (
    <div className="parent-portal-wrapper">
      {/* 1. ÜST NAVBAR */}
      <ParentNavbar
        user={user}
        activeTab={activeTab}
        onTabChange={(tab) => {
          setActiveTab(tab);
          window.location.hash = tab;
        }}
        onLogout={onLogout}
      />

      {/* 2. DİNAMİK ANA İÇERİK ALANI (Görünüm Yönlendirmeleri) */}
      <main className="parent-main-content">
        <div className="parent-content-container">
          {activeTab === 'children' && (
            <ChildrenView
              user={user}
              backendUrl={backendUrl}
              onStartStory={handleStartStory}
            />
          )}

          {activeTab === 'stories' && (
            <StoriesView
              selectedChild={selectedChildForStory}
              onNavigateToChildren={() => setActiveTab('children')}
            />
          )}

          {activeTab === 'bibles' && (
            <BiblesView />
          )}

          {activeTab === 'wallet' && (
            <WalletView user={user} />
          )}
        </div>
      </main>

      {/* 3. ALT FOOTER */}
      <ParentFooter />
    </div>
  );
}
