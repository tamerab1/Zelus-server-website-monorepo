# Zelus Web Portal

![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-ES2024-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-1.25-009639?style=for-the-badge&logo=nginx&logoColor=white)

A production-deployed, full-featured React SPA serving as the community web portal for **Zelus RSPS** — a custom RuneScape private server. The portal provides player authentication, a live donation store (Stripe & PayPal), a voting reward system, real-time game event feeds, and a full hiscores leaderboard — all without any client-side routing library.

---

## 🌐 Live Demo

**[https://zelusrsps.com](https://zelusrsps.com)**

---

## Project Overview

The Zelus Web Portal is a modern, reactive single-page application built with React 18 and Vite. It serves as the primary interface between the gaming community and the backend services, handling:

- **Player accounts** — email-verified registration, secure login, password reset
- **Donation store** — Stripe and PayPal checkout with in-game fulfillment via `::claim`
- **Voting rewards** — topsite vote tracking with 12-hour cooldown timers and in-game `::claimvote` redemption
- **Live game data** — real-time PvP kill feed, hiscores, and server status, polled directly from the game server API
- **Admin panel** — privileged dashboard for user and transaction management

The backend is a FastAPI (Python) service that acts as a bridge between this React application, a PostgreSQL database, and the game server's JSON-based character data. This README focuses exclusively on the React frontend.

---

## Key Features

### React-Specific Engineering

- **State-machine SPA routing** — custom URL-synchronised view switcher using `window.history.pushState` and a `PATH_TO_VIEW` lookup map, with no dependency on React Router
- **Context API global state** — `AppContext` manages authentication, navigation, and checkout state across the entire component tree via a single `useApp()` custom hook
- **Services abstraction layer** — all HTTP calls are centralised in `src/services/`, built on a typed `apiFetch` base client that injects the API base URL from environment variables and throws typed `ServerUnreachableError` for network failures
- **Live event polling** — `LiveFeed` polls the game event API every 7 seconds with `useEffect` interval cleanup, JSON-diffing to skip redundant re-renders, and per-item entry animations for new events
- **Vote card state machines** — each `VoteCard` manages a four-state FSM (`idle → loading → pending → cooldown → idle`) with a self-resetting `setInterval` countdown that auto-transitions back to `idle` when the cooldown expires
- **Dynamic hiscores table** — a single API fetch for 100 players supports 27 distinct views (4 PvP categories + 23 OSRS skills) with instant client-side column switching and local search filtering — zero additional requests
- **Email verification pipeline** — full register → verify-email → login flow using one-time URL tokens read from `URLSearchParams` in `VerifyEmailView` and `ResetPasswordView`
- **Stripe & PayPal checkout** — `CheckoutModal` initiates provider-hosted checkout sessions; `AppContext` reads the `?payment=` query param on redirect-back and renders `PaymentResultView` accordingly
- **Responsive design** — mobile-first Tailwind CSS grid system with `sm:`, `md:`, `lg:`, and `xl:` breakpoints; mobile hamburger navigation with outside-click dismissal via a `window`-level event listener

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | React 18 |
| Build Tool | Vite 8 |
| Styling | Tailwind CSS 3 + custom CSS (fantasy/RPG theme) |
| State Management | React Context API (no Redux/Zustand) |
| HTTP Client | Custom `apiFetch` wrapper (no Axios) |
| Payment | Stripe Checkout, PayPal Orders API (via backend) |
| CAPTCHA | Cloudflare Turnstile (dynamically injected) |
| Fonts | Google Fonts (fantasy serif) |
| Icons | Inline SVG, emoji, OSRS Wiki sprite images |
| Containerisation | Docker (multi-stage: node:20-alpine → nginx:1.25-alpine) |

---

## Architecture Highlights

### AppContext — Global State

`src/context/AppContext.jsx` is the application's single source of truth. It wraps the root at `main.jsx` and exposes state via the `useApp()` custom hook:

```js
const {
  currentUser,    // Authenticated user object (hydrated from localStorage)
  currentView,    // Active page identifier — drives conditional rendering in App.jsx
  setCurrentView, // Navigates to a new view + updates browser URL via History API
  handleCheckout, // Opens CheckoutModal for a given store package
  storeMessage,   // Purchase success/error feedback
  authStatus,     // Login/register feedback
} = useApp();
```

Navigation is URL-synchronised without React Router:

```js
// Bidirectional map: URL path ↔ view name
const PATH_TO_VIEW = {
  '/':               'home',
  '/store':          'store',
  '/vote':           'vote',
  '/hiscores':       'hiscores',
  '/verify-email':   'verify_email',
  '/reset-password': 'reset_password',
};
```

### Services Layer — API Abstraction

All API communication flows through `src/services/api.js`, which provides a typed `apiFetch` wrapper:

```js
// Base URL comes exclusively from the VITE_API_URL environment variable
export async function apiFetch(path, options = {}) { ... }
export class ServerUnreachableError extends Error {}
```

Domain-specific service modules build on this base:

```
services/
├── api.js            ← Base fetch client, error types
├── authService.js    ← login(), register()
├── gameService.js    ← fetchHiscores()
├── voteService.js    ← submitVote(), fetchVoteStatus(), checkGameUsername()
├── paymentService.js ← createStripeCheckout(), createPayPalCheckout()
└── adminService.js   ← fetchAdminUsers(), fetchAdminDonations()
```

No component imports `fetch` directly. This means the API base URL, error handling, and response parsing are changed in one place and propagate everywhere automatically.

---

## Local Installation & Setup

### Prerequisites

- **Node.js** v18 or higher
- **npm** v9 or higher
- A running instance of the Zelus Website API (or point `VITE_API_URL` at the production API)

### Steps

**1. Clone the repository**

```bash
git clone https://github.com/tamerab1/zelus-website-client.git
cd zelus-website-client
```

**2. Install dependencies**

```bash
npm install
```

**3. Configure environment variables**

Create a `.env` file in the project root:

```env
# Required — URL of the running FastAPI backend
VITE_API_URL=http://localhost:8000

# Optional — Cloudflare Turnstile public site key
# Leave empty to disable CAPTCHA in local development
VITE_TURNSTILE_SITE_KEY=
```

> **Note:** In production, `VITE_API_URL` is set to `https://api.zelus.gg` via Docker build argument. Locally, point it at your running API instance.

**4. Start the development server**

```bash
npm run dev
```

The application will be available at `http://localhost:5173`.

**5. Build for production**

```bash
npm run build       # Outputs to dist/
npm run preview     # Preview the production build locally
```

---

## Folder Structure

```
src/
│
├── main.jsx                    # Entry point — mounts AppProvider + App
├── App.jsx                     # Conditional view renderer (SPA router)
├── App.css                     # App-level style overrides
├── index.css                   # Global styles: Tailwind imports, custom RPG theme
│
├── context/
│   └── AppContext.jsx           # Global state: auth, navigation, checkout, session
│
├── services/
│   ├── api.js                  # Base apiFetch client + ServerUnreachableError
│   ├── authService.js          # login(), register()
│   ├── gameService.js          # fetchHiscores()
│   ├── voteService.js          # submitVote(), fetchVoteStatus(), checkGameUsername()
│   ├── paymentService.js       # createStripeCheckout(), createPayPalCheckout()
│   └── adminService.js         # fetchAdminUsers(), fetchAdminDonations()
│
├── utils/
│   ├── ranks.js                # Donor rank tier definitions, admin privilege checks
│   └── hiscores.js             # OSRS XP table, xpToLevel(), SKILL_DEFS (23 skills)
│
├── data/
│   ├── storePackages.js        # 5 donator rank packages (Donator → Sponsor)
│   ├── storePacks.js           # 6 item packs (Starter → Ultimate)
│   └── voteSites.js            # Voting site configs (RuneLocus, RSPS-List)
│
├── components/
│   │
│   ├── layout/
│   │   ├── Navbar.jsx          # Fixed nav: auth state, mobile hamburger, admin link
│   │   └── Footer.jsx          # Links: quick nav, community, legal, social icons
│   │
│   ├── home/
│   │   ├── HomeView.jsx        # Home page container
│   │   ├── Hero.jsx            # Hero section, animated logo, 30s online count poll
│   │   ├── LiveFeed.jsx        # Real-time game event stream (7s polling)
│   │   ├── HiscoresWidget.jsx  # Top-10 leaderboard widget
│   │   ├── FeaturedStore...    # 2 highlighted store packages
│   │   └── ServerStatus...     # Server status badge + CTA buttons
│   │
│   ├── auth/
│   │   ├── LoginForm.jsx       # Login form with auth feedback
│   │   ├── RegisterForm.jsx    # Registration + Cloudflare Turnstile CAPTCHA
│   │   ├── ForgotPassword...   # Enumeration-safe password reset request
│   │   ├── ResetPassword...    # Token-based password reset (reads ?token= from URL)
│   │   └── VerifyEmailView.jsx # Email verification (auto-calls API on mount)
│   │
│   ├── store/
│   │   ├── StoreView.jsx       # Donator ranks + item packs grid
│   │   ├── CheckoutModal.jsx   # Stripe + PayPal checkout modal
│   │   └── PaymentResult...    # Post-payment success / cancelled view
│   │
│   ├── vote/
│   │   ├── VoteView.jsx        # Vote page: login gate, username verify gate, site grid
│   │   └── VoteCard.jsx        # Per-site FSM card with countdown timer
│   │
│   ├── hiscores/
│   │   └── HiscoresView.jsx    # 27-category leaderboard (PvP + 23 skills), local search
│   │
│   ├── download/
│   │   └── DownloadView.jsx    # Platform cards (Windows, macOS, Linux) + how-it-works
│   │
│   ├── account/
│   │   └── AccountPanel.jsx    # User profile: rank, tokens, total donated
│   │
│   ├── admin/
│   │   └── AdminDashboard.jsx  # Privilege-gated: users table + transactions table
│   │
│   └── ui/
│       ├── AlertBox.jsx        # Typed alert banner (success / error / info)
│       └── LoadingSpinner.jsx  # Spinner component + ServerDownBanner
│
└── assets/
    ├── hero.jpg                # Full-viewport hero background image
    └── logo.png                # Zelus logo
```

---

## Docker & Production Deployment

The React app is shipped as a **minimal, production-hardened Docker image** using a two-stage build. No Node.js runtime exists in the final image — only compiled static files served by nginx.

### Dockerfile (multi-stage build)

```
Stage 1 — Builder (node:20-alpine)
  ├── npm install
  ├── npm run build        ← Vite compiles src/ → dist/
  └── Output: dist/

Stage 2 — Server (nginx:1.25-alpine)
  ├── COPY dist/ → /usr/share/nginx/html
  └── COPY docker-nginx.conf → /etc/nginx/conf.d/default.conf
```

The nginx config serves the SPA correctly by routing all paths to `index.html` (required for the History API-based routing):

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### Full Stack with Docker Compose

In production the React app runs as one of five containers managed by Docker Compose on a Hetzner Cloud VPS:

| Container | Image | Role |
|---|---|---|
| `zelus_nginx` | nginx:1.25-alpine | Reverse proxy, SSL termination (Let's Encrypt) |
| `zelus_website` | zelus/website | React app (this repo) served via nginx |
| `zelus_api` | zelus/api | FastAPI Python backend |
| `zelus_postgres` | postgres:16 | Web application database |
| `zelus_game` | zelus/game-server | Java game server |

### Building & Running the Website Container

**Build the image:**

```bash
docker build \
  --build-arg VITE_API_URL=https://api.zelus.gg \
  --build-arg VITE_TURNSTILE_SITE_KEY=your_key \
  -t zelus/website .
```

**Run standalone (for testing):**

```bash
docker run -p 8080:80 zelus/website
# Visit http://localhost:8080
```

**Run as part of the full stack:**

```bash
# From the root of the monorepo (where docker-compose.yml lives)
docker compose --profile web up -d
```

### Environment Variables at Build Time

Vite bakes environment variables into the static bundle at build time via `--build-arg`. These are the only two variables the React app needs:

| Build Arg | Production Value | Description |
|---|---|---|
| `VITE_API_URL` | `https://api.zelus.gg` | FastAPI backend base URL |
| `VITE_TURNSTILE_SITE_KEY` | Cloudflare public key | CAPTCHA site key (safe to expose) |

> No secrets are embedded in the Docker image. All backend secrets (Stripe keys, SMTP credentials, database passwords) live exclusively in the API container's environment, passed via the Docker Compose `env_file` directive.

### Production Infrastructure

```
Internet
   │
   ▼
Cloudflare CDN (DDoS protection, caching)
   │
   ▼
Hetzner Cloud VPS — Ubuntu 22.04
   │
   ▼
Docker Compose (5 containers, zelus_internal network)
   ├── zelus_nginx      → Ports 80/443, terminates SSL, proxies to website & api
   ├── zelus_website    → Serves React static build (internal only)
   ├── zelus_api        → FastAPI on port 8000 (internal only)
   ├── zelus_postgres   → PostgreSQL (internal only)
   └── zelus_game       → Java game server
```

SSL certificates are managed automatically by **Certbot** (Let's Encrypt), renewed via a cron job on the VPS. The nginx container serves `https://zelusrsps.com` with A+ TLS configuration.

---

## Licence

This project is proprietary software. All rights reserved — Zelus Project 2026.

---

> Built with React 18 · Deployed at [zelusrsps.com](https://zelusrsps.com)
