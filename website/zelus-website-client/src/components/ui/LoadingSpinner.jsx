/** Centred animated spinner with optional label */
export default function LoadingSpinner({ text = 'Loading...' }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-4">
      <div
        className="w-10 h-10 rounded-full border-2 border-zelus-border border-t-zelus-gold animate-spin"
      />
      <p className="font-fantasy text-xs tracking-widest" style={{ color: '#555' }}>
        {text}
      </p>
    </div>
  );
}

/** Full-width error state shown when the API server is unreachable */
export function ServerDownBanner({ message }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 gap-3 text-center px-4">
      <span className="text-3xl">⚠</span>
      <p className="font-fantasy text-sm tracking-widest text-red-500">SERVER UNREACHABLE</p>
      <p className="font-sans text-xs leading-relaxed" style={{ color: '#666', maxWidth: 280 }}>
        {message}
      </p>
    </div>
  );
}
