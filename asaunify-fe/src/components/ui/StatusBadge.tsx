interface StatusBadgeProps {
  status: string;
}

const STATUS_STYLES: Record<string, string> = {
  APPROVED: 'text-status-approved',
  COMPLETED: 'text-status-approved',
  REJECTED: 'text-status-rejected',
  PENDING: 'text-status-pending',
  DRAFT: 'text-gray-400',
};

function formatLabel(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const colorClass = STATUS_STYLES[status] ?? 'text-gray-500';
  return (
    <span className={`text-sm font-semibold ${colorClass}`}>
      {formatLabel(status)}
    </span>
  );
}