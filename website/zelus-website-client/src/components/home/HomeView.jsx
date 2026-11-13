import { useEffect, useState } from 'react';
import { fetchHiscores } from '../../services/gameService.js';
import useLiveFeed from '../../hooks/useLiveFeed.js';
import Hero from './Hero.jsx';
import PulseStrip from './PulseStrip.jsx';
import FeatureShowcase from './FeatureShowcase.jsx';
import LeaderboardSection from './LeaderboardSection.jsx';

export default function HomeView() {
  const [hiscores, setHiscores] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);
  const feed = useLiveFeed(12);

  useEffect(() => {
    // 50 rows: the top 5 fill the leaderboard, the rest let the champion card find the top killer.
    fetchHiscores('total_level', 50)
      .then(data => setHiscores(Array.isArray(data) ? data : []))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <Hero />
      <PulseStrip feed={feed} hiscores={hiscores} loading={loading} />
      <FeatureShowcase />
      <LeaderboardSection hiscores={hiscores} loading={loading} error={error} />
    </>
  );
}
