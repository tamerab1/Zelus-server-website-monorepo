/**
 * NewsSpotlight — the two-column block under the hero.
 *   left  (65%): Latest Updates & Patch Notes, cards from data/news.js; Read More opens the full post in a modal
 *   right (35%): featured video (or a "coming soon" card), Discord card with live counts, quick links
 * Stacks to one column below lg.
 */
import { useEffect, useState } from 'react';
import { NEWS, NEWS_AUTHOR, formatNewsDate } from '../../data/news.js';
import { DISCORD_URL, FEATURED_VIDEO_ID } from '../../data/links.js';
import { useApp } from '../../context/AppContext.jsx';
import useDiscordInvite from '../../hooks/useDiscordInvite.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';
import './HomeSections.css';

/** Banner background: the post's picture (if any) under a dark fade so the title stays readable, else its gradient. */
const coverBg = (post) => (post.image
  ? `linear-gradient(180deg, rgba(11,12,16,0.05) 35%, rgba(11,12,16,0.85)), url(${post.image}) center / cover no-repeat`
  : post.cover);

function SectionHead({ children }) {
  return (
    <div className="zh-sec-head">
      <h2 className="zh-sec-title font-fantasy">{children}</h2>
      <div className="zh-sec-line" />
    </div>
  );
}

function AuthorBadge() {
  return (
    <span className="zh-author">
      <span className="zh-author-av">Z</span>
      {NEWS_AUTHOR.name}
      <span className="zh-author-role">{NEWS_AUTHOR.role}</span>
    </span>
  );
}

function NewsCard({ post, onOpen }) {
  return (
    <article className="zh-glass zh-news" style={{ '--a': post.accent }}>
      <div className="zh-news-cover" style={{ background: coverBg(post) }}>
        <span className="zh-news-tag">{post.tag}</span>
        <span className="zh-news-cover-title font-fantasy">{post.title}</span>
      </div>
      <div className="zh-news-body">
        <div className="zh-news-meta">
          <time dateTime={post.date}>{formatNewsDate(post.date)}</time>
          <AuthorBadge />
        </div>
        <h3 className="zh-news-title">{post.title}</h3>
        <p className="zh-news-snippet">{post.snippet}</p>
        <button type="button" className="zh-more" onClick={() => onOpen(post)}>READ MORE →</button>
      </div>
    </article>
  );
}

function NewsModal({ post, onClose }) {
  useEffect(() => {
    if (!post) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', onKey); };
  }, [post, onClose]);

  if (!post) return null;
  return (
    <div className="zh-overlay" role="dialog" aria-modal="true" aria-label={post.title}
      onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="zh-glass zh-modal">
        <button type="button" className="zh-close" onClick={onClose} aria-label="Close">×</button>
        <div className={`zh-modal-cover ${post.image ? 'zh-modal-cover--img' : ''}`} style={{ background: coverBg(post) }}>
          <h2 className="font-fantasy">{post.title}</h2>
        </div>
        <div className="zh-modal-body">
          <div className="zh-news-meta">
            <time dateTime={post.date}>{formatNewsDate(post.date)}</time>
            <AuthorBadge />
          </div>
          {post.body.map((block, i) => {
            // a body block is a paragraph string, { heading }, or { image, alt, caption } (in-game screenshots)
            if (typeof block === 'string') return <p key={i}>{block}</p>;
            if (block.heading) return <h3 key={i} className="zh-modal-h font-fantasy">{block.heading}</h3>;
            return (
              <figure key={i} className="zh-modal-fig">
                <img src={block.image} alt={block.alt || ''} loading="lazy" />
                {block.caption && <figcaption>{block.caption}</figcaption>}
              </figure>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/**
 * The trailer as a click-to-play facade: the thumbnail and a play button until it's clicked, then the
 * youtube-nocookie player (autoplaying). Nothing from YouTube's player loads for visitors who never press play.
 */
function MediaCard() {
  const [playing, setPlaying] = useState(false);
  return (
    <section className="zh-glass zh-card" aria-labelledby="zh-media">
      <h3 className="zh-card-title font-fantasy" id="zh-media">FEATURED MEDIA</h3>
      <div className="zh-video">
        {FEATURED_VIDEO_ID && playing ? (
          <iframe
            src={`https://www.youtube-nocookie.com/embed/${FEATURED_VIDEO_ID}?autoplay=1&rel=0`}
            title="Zelus trailer"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        ) : FEATURED_VIDEO_ID ? (
          <button
            type="button"
            className="zh-video-facade"
            onClick={() => setPlaying(true)}
            aria-label="Play the Zelus trailer"
            style={{ backgroundImage: `url(https://i.ytimg.com/vi/${FEATURED_VIDEO_ID}/hqdefault.jpg)` }}
          >
            <span className="zh-play" aria-hidden="true" />
            <span className="zh-video-facade-label">Watch the Zelus trailer</span>
          </button>
        ) : (
          <div className="zh-video-soon">
            <div>
              <div className="zh-play" aria-hidden="true" />
              <strong style={{ color: '#f0f6fc' }}>Trailer coming soon</strong>
              <div style={{ color: '#c3cbd6', marginTop: 4 }}>Boss guides and update showcases will appear here.</div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

function DiscordCard() {
  const info = useDiscordInvite();
  return (
    <section className="zh-glass zh-card" aria-labelledby="zh-discord">
      <h3 className="zh-card-title font-fantasy" id="zh-discord">COMMUNITY</h3>
      <div className="zh-discord-head">
        <div className="zh-discord-logo"><DiscordIcon size={26} color="#fff" /></div>
        <div>
          <div className="zh-discord-name">{info?.name ?? 'Zelus'} Discord</div>
          <div className="zh-discord-stats">
            {info?.online != null && <span><span className="zh-dot" /> <b>{info.online.toLocaleString()}</b> online</span>}
            {info?.members != null && <span><b>{info.members.toLocaleString()}</b> members</span>}
            {info == null && <span>Announcements, support & giveaways</span>}
          </div>
        </div>
      </div>
      <p className="zh-discord-teaser">
        Patch notes land here first. Find PvP partners, report bugs and grab giveaway codes.
      </p>
      <a className="zh-discord-btn" href={DISCORD_URL} target="_blank" rel="noopener noreferrer">
        <DiscordIcon size={16} color="#fff" /> Join Discord
      </a>
    </section>
  );
}

function QuickLinks() {
  const { setCurrentView } = useApp();
  const go = (view) => () => { setCurrentView(view); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  return (
    <section className="zh-glass zh-card" aria-labelledby="zh-links">
      <h3 className="zh-card-title font-fantasy" id="zh-links">QUICK LINKS</h3>
      <div className="zh-links">
        <a className="zh-link-pill" href={DISCORD_URL} target="_blank" rel="noopener noreferrer">
          <DiscordIcon size={14} color="#c7cbff" /> Discord
        </a>
        <button type="button" className="zh-link-pill" onClick={go('download')}>⬇ Play Now</button>
        <button type="button" className="zh-link-pill" onClick={go('hiscores')}>🏆 Hiscores</button>
        <button type="button" className="zh-link-pill" onClick={go('vote')}>✔ Vote</button>
        <button type="button" className="zh-link-pill" onClick={go('store')}>💎 Store</button>
      </div>
    </section>
  );
}

export default function NewsSpotlight() {
  const [open, setOpen] = useState(null);
  return (
    <section className="max-w-7xl mx-auto px-4 sm:px-6 pt-4 pb-6">
      <div className="zh-spot">
        <div>
          <SectionHead>LATEST UPDATES & PATCH NOTES</SectionHead>
          <div className="zh-news-list">
            {NEWS.map((post) => <NewsCard key={post.id} post={post} onOpen={setOpen} />)}
          </div>
        </div>
        <aside>
          <SectionHead>MEDIA & COMMUNITY</SectionHead>
          <div className="zh-side">
            <MediaCard />
            <DiscordCard />
            <QuickLinks />
          </div>
        </aside>
      </div>
      <NewsModal post={open} onClose={() => setOpen(null)} />
    </section>
  );
}
