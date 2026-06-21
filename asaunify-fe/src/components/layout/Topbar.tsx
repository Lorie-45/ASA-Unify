import { Calendar, Bell } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { useNavigate } from 'react-router-dom';
import { useNotificationStore } from '../../store/notificationStore';

function getInitials(fullName: string): string {
  const parts = fullName.trim().split(' ');
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function formatRoleLabel(role: string): string {
  return role
    .toLowerCase()
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

export default function Topbar() {
  const navigate = useNavigate();
  const fullName = useAuthStore((state) => state.fullName);
  const role = useAuthStore((state) => state.role);
  const unreadCount = useNotificationStore((state) => state.unreadCount);

  const today = new Date();
  const dateLabel = today.toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });
  const dayLabel = today.toLocaleDateString('en-GB', { weekday: 'long' });

  return (
    <header className="flex items-center justify-between border-b border-gray-200 px-8 py-5 bg-white">
      {/* Date */}
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-primary flex items-center justify-center text-white">
          <Calendar size={20} />
        </div>
        <div>
          <p className="text-sm font-bold text-gray-900 leading-tight">
            {dateLabel}
          </p>
          <p className="text-xs text-gray-500">{dayLabel}</p>
        </div>
      </div>

      {/* Notification + User */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/notifications')}
          className="relative w-10 h-10 rounded-full bg-primary flex items-center justify-center text-white hover:bg-primary-dark transition-colors"
        >
          <Bell size={18} />
          {unreadCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] rounded-full min-w-4.5 h-4.5 flex items-center justify-center px-1">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>

        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-primary flex items-center justify-center text-white font-semibold text-sm">
            {fullName ? getInitials(fullName) : '?'}
          </div>
          <div>
            <p className="text-sm font-bold text-gray-900 leading-tight">
              {fullName}
            </p>
            <p className="text-xs text-gray-500">
              {role ? formatRoleLabel(role) : ''}
            </p>
          </div>
        </div>
      </div>
    </header>
  );
}