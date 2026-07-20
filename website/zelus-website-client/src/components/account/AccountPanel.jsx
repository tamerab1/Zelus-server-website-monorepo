import { useApp } from '../../context/AppContext.jsx';
import { getDonorRank } from '../../utils/ranks.js';

export default function AccountPanel() {
  const { currentUser, setCurrentView, handleLogout } = useApp();

  if (!currentUser) return null;

  const rank = getDonorRank(currentUser.total_spent);

  return (
    <main className="max-w-4xl mx-auto pt-16 px-4 sm:px-6 pb-24">
      <div className="stone-panel" style={{ borderRadius: 2 }}>

        {/* Profile header */}
        <div className="panel-header px-6 sm:px-8 py-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div>
              <p className="font-fantasy text-xs tracking-widest mb-1" style={{ color: '#555' }}>
                ADVENTURER PROFILE
              </p>
              <h2 className="font-fantasy text-xl sm:text-2xl font-bold text-white">
                {currentUser.username}
              </h2>
              <p className="font-mono text-xs mt-1" style={{ color: '#444' }}>{currentUser.email}</p>
            </div>
            <div className="text-left sm:text-right">
              <p className="font-fantasy text-xs tracking-widest mb-1" style={{ color: '#555' }}>STATUS</p>
              <span className={`font-fantasy font-bold text-lg tracking-wide ${rank.color}`}>
                {rank.name}
              </span>
            </div>
          </div>
        </div>

        {/* Stats */}
        <div
          className="grid grid-cols-1 sm:grid-cols-3"
          style={{ borderBottom: '1px solid #1e1a14', gap: 1, background: '#1e1a14' }}
        >
          {[
            { label: 'Store Tokens',   value: currentUser.tokens,            color: '#d4af37' },
            { label: 'Total Donated',  value: `$${currentUser.total_spent}`, color: '#4ade80' },
            { label: 'Permissions',    value: currentUser.privilege,         color: '#60a5fa' },
          ].map(({ label, value, color }) => (
            <div key={label} className="text-center py-8" style={{ background: '#0a0a0e' }}>
              <p className="font-fantasy text-xs tracking-widest mb-3" style={{ color: '#555' }}>
                {label}
              </p>
              <p className="font-fantasy text-xl sm:text-2xl font-bold" style={{ color }}>
                {value}
              </p>
            </div>
          ))}
        </div>

        {/* Actions */}
        <div className="p-8 flex flex-col sm:flex-row justify-center gap-4">
          <button
            onClick={() => setCurrentView('store')}
            className="btn-download py-3 px-10 sm:px-12 font-fantasy text-sm tracking-widest uppercase"
          >
            Visit Store
          </button>
          <button
            onClick={handleLogout}
            className="py-3 px-10 sm:px-12 font-fantasy text-sm tracking-widest uppercase transition-colors"
            style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid #1e1a14', borderRadius: 3, color: '#555' }}
            onMouseOver={e => e.currentTarget.style.color = '#f87171'}
            onMouseOut={e  => e.currentTarget.style.color = '#555'}
          >
            Logout
          </button>
        </div>
      </div>
    </main>
  );
}
