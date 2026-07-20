import { useApp } from './context/AppContext.jsx';
import { isAdmin } from './utils/ranks.js';
import heroBg from './assets/hero.jpg';

import Navbar             from './components/layout/Navbar.jsx';
import Footer             from './components/layout/Footer.jsx';
import HomeView           from './components/home/HomeView.jsx';
import StoreView          from './components/store/StoreView.jsx';
import CheckoutModal      from './components/store/CheckoutModal.jsx';
import PaymentResultView  from './components/store/PaymentResultView.jsx';
import LoginForm           from './components/auth/LoginForm.jsx';
import RegisterForm        from './components/auth/RegisterForm.jsx';
import ForgotPasswordForm  from './components/auth/ForgotPasswordForm.jsx';
import ResetPasswordView   from './components/auth/ResetPasswordView.jsx';
import VerifyEmailView     from './components/auth/VerifyEmailView.jsx';
import AccountPanel       from './components/account/AccountPanel.jsx';
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
      {currentView === 'login'           && <LoginForm />}
      {currentView === 'register'        && <RegisterForm />}
      {currentView === 'forgot_password' && <ForgotPasswordForm />}
      {currentView === 'reset_password'  && <ResetPasswordView />}
      {currentView === 'verify_email'    && <VerifyEmailView />}
      {currentView === 'panel'          && currentUser && <AccountPanel />}
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

export default function App() {
  const { checkoutPkg, setCheckoutPkg, currentUser } = useApp();

  return (
    <div className="min-h-screen text-white font-fantasy flex flex-col"
         style={{
           backgroundImage: `url(${heroBg})`,
           backgroundSize: 'cover',
           backgroundPosition: 'center',
           backgroundAttachment: 'fixed',
         }}>
      <div className="flex flex-col min-h-screen">
        <Navbar />
        {/* Spacer to offset fixed navbar height */}
        <div style={{ height: '67px', flexShrink: 0 }} />
        <div className="flex-grow">
          <AppRoutes />
        </div>
        <Footer />
      </div>

      {/* Checkout modal — rendered as a full-screen overlay above everything */}
      {checkoutPkg && (
        <CheckoutModal
          pkg={checkoutPkg}
          currentUser={currentUser}
          onClose={() => setCheckoutPkg(null)}
        />
      )}
    </div>
  );
}
