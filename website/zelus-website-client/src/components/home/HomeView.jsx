import { useState, useEffect } from 'react';
import { fetchHiscores } from '../../services/gameService.js';
import Hero from './Hero.jsx';
import HiscoresWidget from './HiscoresWidget.jsx';
import FeaturedStoreWidget from './FeaturedStoreWidget.jsx';
import ServerStatusWidget from './ServerStatusWidget.jsx';
import LiveFeed from './LiveFeed.jsx';

export default function HomeView() {
  const [hiscores, setHiscores] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);

  useEffect(() => {
    fetchHiscores()
      .then(data => { setHiscores(data); setLoading(false); })
      .catch(err  => { setError(err.message); setLoading(false); });
  }, []);

  return (
    <>
      <Hero />

      {/* Live feed — full width strip below hero */}
      <section className="max-w-7xl mx-auto pt-10 px-4 sm:px-6">
        <LiveFeed />
      </section>

      {/* 3-column dashboard */}
      <section className="max-w-7xl mx-auto py-8 px-4 sm:px-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        <HiscoresWidget   hiscores={hiscores} loading={loading} error={error} />
        <FeaturedStoreWidget />
        <ServerStatusWidget hiscores={hiscores} loading={loading} />
      </section>
    </>
  );
}
