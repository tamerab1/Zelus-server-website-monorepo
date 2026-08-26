const colorMap = {
  error:   'bg-red-950/30 border-red-800 text-red-300',
  success: 'bg-green-950/30 border-green-800 text-green-300',
  info:    'bg-blue-950/30 border-blue-800 text-blue-300',
};

/**
 * Inline alert banner for form feedback and API messages.
 * Renders nothing when status.message is empty.
 */
export default function AlertBox({ status, className = '' }) {
  if (!status?.message) return null;
  const cls = colorMap[status.type] ?? colorMap.info;
  return (
    <div
      className={`p-3 mb-6 text-xs text-center font-bold font-fantasy tracking-widest border ${cls} ${className}`}
      style={{ borderRadius: 2 }}
    >
      {status.message}
    </div>
  );
}
