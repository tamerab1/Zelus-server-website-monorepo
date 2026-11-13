import { useApp } from './context/AppContext.jsx';
import { isAdmin } from './utils/ranks.js';
import heroArt from './assets/karn-hero.webp';

import Navbar             from './components/layout/Navbar.jsx';
import Footer             from './components/layout/Footer.jsx';
import PlayModal          from './components/layout/PlayModal.jsx';
import HomeView           from './components/home/HomeView.jsx';
import StoreView          from './components/store/StoreView.jsx';
import CheckoutModal      from './components/store/CheckoutModal.jsx';
import PaymentResultView  from './components/store/PaymentResultView.jsx';
import LoginForm          from './components/auth/LoginForm.jsx';
import AdminDashboard     from './components/admin/AdminDashboard.jsx';
import VoteView           from './components/vote/VoteView.jsx';
import HiscoresView       from './components/hiscores/HiscoresView.jsx';
import DownloadView       from './components/download/DownloadView.jsx';

function AppRoutes() {
  const { currentView, currentUser, paymentResult, setCurrentView } = useApp();

  return (
    <>
      {currentView === 'home'           && <HomeView />}
      {currentView === 'store'          && <StoreView />}
      {currentView === 'staff_login'    && <LoginForm />}
      {currentView === 'admin'          && isAdmin(currentUser?.privilege) && <AdminDashboard />}
      {currentView === 'vote'           && <VoteView />}
      {currentView === 'hiscores'       && <HiscoresView />}
      {currentView === 'download'       && <DownloadView />}
      {currentView === 'payment_result' && (
        <PaymentResultView
          result={paymentResult}
          onGoToStore={() => setCurrentView('store')}
          onGoHome={() => setCurrentView('home')}
        />
      )}
    </>
  );
}

/** Sub-pages sit on the void with a faint slice of the Karn artwork fading out under the header. */
function PageBackdrop() {
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 h-[560px] overflow-hidden">
      <img src={heroArt} alt="" className="w-full h-full object-cover object-[center_35%] opacity-[0.22] blur-[2px] scale-105" />
      <div className="absolute inset-0" style={{ background: 'linear-gradient(180deg, rgba(9,8,10,0.35) 0%, rgba(9,8,10,0.85) 60%, #09080A 100%)' }} />
      <div className="absolute inset-0" style={{ background: 'radial-gradient(60% 50% at 50% 0%, rgba(184,29,36,0.18), transparent 70%)' }} />
    </div>
  );
}

export default function App() {
  const { currentView, checkoutPkg, setCheckoutPkg } = useApp();
  const isHome = currentView === 'home';

  return (
    <div className="relative min-h-screen bg-karn-void text-karn-text font-sans flex flex-col">
      {!isHome && <PageBackdrop />}
      <Navbar />
      {/* The home hero runs under the header; every other page starts below it. */}
      {!isHome && <div style={{ height: 'var(--k-header-h)', flexShrink: 0 }} />}
      <main className="relative flex-grow">
        <AppRoutes />
      </main>
      <Footer />

      <PlayModal />
      {checkoutPkg && (
        <CheckoutModal
          pkg={checkoutPkg}
          onClose={() => setCheckoutPkg(null)}
        />
      )}
    </div>
  );
}
