import { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { fetchAdminUsers, fetchAdminDonations } from '../../services/adminService.js';
import LoadingSpinner, { ServerDownBanner } from '../ui/LoadingSpinner.jsx';

function AdminTable({ columns, rows, renderRow }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left min-w-[400px]">
        <thead>
          <tr className="font-fantasy text-xs tracking-widest"
              style={{ color: '#555', borderBottom: '1px solid #1e1a14' }}>
            {columns.map(col => (
              <th key={col.key} className={`p-4 ${col.align === 'right' ? 'text-right' : ''}`}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr
              key={i}
              className="transition-colors"
              style={{ borderBottom: '1px solid rgba(30,26,20,0.5)' }}
              onMouseOver={e => e.currentTarget.style.background = 'rgba(255,255,255,0.03)'}
              onMouseOut={e  => e.currentTarget.style.background = 'transparent'}
            >
              {renderRow(row)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function AdminDashboard() {
  const { currentUser } = useApp();
  const [adminUsers,      setAdminUsers]      = useState([]);
  const [adminDonations,  setAdminDonations]  = useState([]);
  const [loading,         setLoading]         = useState(true);
  const [error,           setError]           = useState(null);

  useEffect(() => {
    Promise.all([fetchAdminUsers(), fetchAdminDonations()])
      .then(([users, donations]) => {
        setAdminUsers(users);
        setAdminDonations(donations);
      })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const userColumns = [
    { key: 'id',          label: 'UID'      },
    { key: 'username',    label: 'Username' },
    { key: 'privilege',   label: 'Rank'     },
    { key: 'total_spent', label: 'Spent', align: 'right' },
  ];

  const donationColumns = [
    { key: 'id',           label: 'TXN'     },
    { key: 'package_name', label: 'Package' },
    { key: 'usd_amount',   label: 'Sum'     },
    { key: 'status',       label: 'Status', align: 'right' },
  ];

  return (
    <main className="max-w-7xl mx-auto pt-16 px-4 sm:px-6 pb-24">

      {/* Admin heading */}
      <div
        className="flex flex-col sm:flex-row justify-between items-start sm:items-end pb-6 mb-10 gap-4"
        style={{ borderBottom: '1px solid rgba(127,29,29,0.4)' }}
      >
        <div>
          <p className="font-fantasy text-xs tracking-widest mb-1" style={{ color: '#7f1d1d' }}>
            RESTRICTED ACCESS
          </p>
          <h2 className="font-fantasy text-3xl sm:text-4xl font-bold tracking-widest text-red-600">
            ADMINISTRATION
          </h2>
        </div>
        <span
          className="font-fantasy text-xs tracking-widest px-5 py-2 text-red-400"
          style={{ background: 'rgba(127,29,29,0.15)', border: '1px solid rgba(127,29,29,0.4)', borderRadius: 2 }}
        >
          {currentUser.privilege}
        </span>
      </div>

      {loading && <LoadingSpinner text="Loading administration data..." />}
      {!loading && error && <ServerDownBanner message={error} />}

      {!loading && !error && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">

          {/* Users table */}
          <div className="stone-panel" style={{ borderTopColor: '#dc2626', borderRadius: 2 }}>
            <div className="panel-header flex items-center gap-3">
              <span className="w-2 h-2 rounded-full bg-red-600 inline-block" />
              <span className="font-fantasy text-xs tracking-widest text-white">
                REGISTERED ADVENTURERS ({adminUsers.length})
              </span>
            </div>
            <div className="overflow-y-auto" style={{ maxHeight: 460 }}>
              <AdminTable
                columns={userColumns}
                rows={adminUsers}
                renderRow={u => (
                  <>
                    <td className="p-4 font-mono text-xs" style={{ color: '#555' }}>#{u.id}</td>
                    <td className="p-4 font-fantasy text-sm font-bold text-white">{u.username}</td>
                    <td className="p-4">
                      <span
                        className="font-fantasy text-xs tracking-widest px-2 py-1"
                        style={{
                          background:   u.privilege === 'PLAYER' ? 'rgba(255,255,255,0.03)' : 'rgba(127,29,29,0.2)',
                          border:       `1px solid ${u.privilege === 'PLAYER' ? '#2a2420' : 'rgba(127,29,29,0.5)'}`,
                          color:        u.privilege === 'PLAYER' ? '#555' : '#f87171',
                          borderRadius: 2,
                        }}
                      >
                        {u.privilege}
                      </span>
                    </td>
                    <td className="p-4 text-right font-mono text-xs text-green-500">${u.total_spent}</td>
                  </>
                )}
              />
            </div>
          </div>

          {/* Donations table */}
          <div className="stone-panel" style={{ borderTopColor: '#dc2626', borderRadius: 2 }}>
            <div className="panel-header flex items-center gap-3">
              <span className="w-2 h-2 rounded-full bg-yellow-500 animate-pulse inline-block" />
              <span className="font-fantasy text-xs tracking-widest text-white">
                ACTIVE TRANSACTIONS ({adminDonations.length})
              </span>
            </div>
            <div className="overflow-y-auto" style={{ maxHeight: 460 }}>
              <AdminTable
                columns={donationColumns}
                rows={adminDonations}
                renderRow={d => (
                  <>
                    <td className="p-4 font-mono text-xs" style={{ color: '#555' }}>#{d.id}</td>
                    <td className="p-4 font-fantasy text-sm text-white">{d.package_name}</td>
                    <td className="p-4 font-mono text-xs text-green-500">${d.usd_amount}</td>
                    <td className="p-4 text-right">
                      <span
                        className="font-fantasy text-xs tracking-widest px-3 py-1"
                        style={{
                          background:   d.status === 'pending' ? 'rgba(133,77,14,0.2)' : 'rgba(21,128,61,0.2)',
                          border:       `1px solid ${d.status === 'pending' ? 'rgba(133,77,14,0.5)' : 'rgba(21,128,61,0.5)'}`,
                          color:        d.status === 'pending' ? '#eab308' : '#4ade80',
                          borderRadius: 2,
                        }}
                      >
                        {d.status.toUpperCase()}
                      </span>
                    </td>
                  </>
                )}
              />
            </div>
          </div>

        </div>
      )}
    </main>
  );
}
