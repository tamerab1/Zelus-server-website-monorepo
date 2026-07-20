/**
 * PaymentResultView
 *
 * Shown when the player returns to the site after Stripe/PayPal redirect.
 * The parent App reads `?payment=success|cancelled` from the URL and renders
 * this component; it strips the params after reading them so the user can
 * navigate normally from here.
 */
export default function PaymentResultView({ result, onGoToStore, onGoHome }) {
  const isSuccess = result === 'success';

  return (
    <main className="max-w-lg mx-auto pt-24 px-4 pb-24 text-center">

      {/* Icon */}
      <div className="text-6xl mb-6">
        {isSuccess ? '✅' : '❌'}
      </div>

      {/* Title */}
      <h2
        className="font-fantasy text-3xl font-bold mb-4"
        style={{ color: isSuccess ? '#4ade80' : '#f87171' }}
      >
        {isSuccess ? 'Payment Successful!' : 'Payment Cancelled'}
      </h2>

      {/* Body */}
      {isSuccess ? (
        <div className="space-y-4">
          <p className="text-sm leading-relaxed" style={{ color: '#c8bfb0' }}>
            Your order is being processed. Log into Zelus and type{' '}
            <span
              className="font-mono font-bold px-1.5 py-0.5"
              style={{ background: 'rgba(212,175,55,0.1)', color: '#d4af37', borderRadius: 2 }}
            >
              ::claim
            </span>{' '}
            to receive your items instantly.
          </p>
          <p className="text-xs" style={{ color: '#555' }}>
            If your items don't appear within a few minutes, please contact support
            with your in-game username and order confirmation email.
          </p>
        </div>
      ) : (
        <p className="text-sm leading-relaxed" style={{ color: '#c8bfb0' }}>
          Your payment was cancelled. No charge was made. You can return to the
          store and try again whenever you're ready.
        </p>
      )}

      {/* Actions */}
      <div className="flex gap-3 justify-center mt-8">
        <button
          onClick={onGoToStore}
          className="px-6 py-2.5 font-fantasy text-sm tracking-widest uppercase font-bold rounded-sm transition-all duration-200"
          style={{
            background: 'rgba(212,175,55,0.1)',
            border: '1px solid rgba(212,175,55,0.4)',
            color: '#d4af37',
          }}
          onMouseOver={e => { e.currentTarget.style.background = '#d4af37'; e.currentTarget.style.color = '#0a0a14'; }}
          onMouseOut={e  => { e.currentTarget.style.background = 'rgba(212,175,55,0.1)'; e.currentTarget.style.color = '#d4af37'; }}
        >
          Back to Store
        </button>
        <button
          onClick={onGoHome}
          className="px-6 py-2.5 font-fantasy text-sm tracking-widest uppercase font-bold rounded-sm transition-all duration-200"
          style={{
            background: 'rgba(255,255,255,0.04)',
            border: '1px solid rgba(255,255,255,0.08)',
            color: '#8a8070',
          }}
          onMouseOver={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.2)'; e.currentTarget.style.color = '#c8bfb0'; }}
          onMouseOut={e  => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.08)'; e.currentTarget.style.color = '#8a8070'; }}
        >
          Home
        </button>
      </div>

    </main>
  );
}
