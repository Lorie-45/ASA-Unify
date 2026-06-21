import { useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { useNotificationStore } from '../store/notificationStore';
import Button from '../components/ui/Button';
import { formatDate } from '../utils/formatDate';
import type { NotificationDto } from '../types/notification.types';

export default function NotificationsPage() {
  const navigate = useNavigate();
  const notifications = useNotificationStore((state) => state.notifications);
  const unreadCount = useNotificationStore((state) => state.unreadCount);
  const isLoading = useNotificationStore((state) => state.isLoading);
  const fetchAll = useNotificationStore((state) => state.fetchAll);
  const markAllAsRead = useNotificationStore((state) => state.markAllAsRead);
  const markAsRead = useNotificationStore((state) => state.markAsRead);

  const loadAll = useCallback(() => {
    fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadAll();
  }, [loadAll]);

  async function handleClick(notification: NotificationDto) {
    if (!notification.isRead) {
      markAsRead(notification.id);
    }
    if (notification.requestId) {
      navigate(`/requests/${notification.requestId}`);
    } else if (notification.memoId) {
      navigate(`/memos/${notification.memoId}`);
    }
  }

  return (
    <div className="max-w-2xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold text-gray-900">Notifications</h1>
        {unreadCount > 0 && (
          <Button
            variant="ghost"
            icon={<CheckCheck size={16} />}
            onClick={() => markAllAsRead()}
          >
            Mark all as read
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : notifications.length === 0 ? (
        <div className="border border-dashed border-gray-200 rounded-xl p-12 text-center text-gray-400 text-sm flex flex-col items-center gap-2">
          <Bell size={28} className="text-gray-300" />
          You have no notifications yet.
        </div>
      ) : (
        <div className="space-y-2">
          {notifications.map((notification) => (
            <button
              key={notification.id}
              onClick={() => handleClick(notification)}
              className={`w-full text-left flex items-start gap-3 px-4 py-4 rounded-xl border transition-colors ${
                notification.isRead
                  ? 'border-gray-100 bg-white'
                  : 'border-primary/30 bg-primary/5'
              } hover:bg-gray-50`}
            >
              <div
                className={`w-2 h-2 rounded-full mt-2 shrink-0 ${
                  notification.isRead ? 'bg-transparent' : 'bg-primary'
                }`}
              />
              <div className="flex-1">
                <p className="text-sm font-semibold text-gray-900">
                  {notification.title}
                </p>
                <p className="text-sm text-gray-600 mt-0.5">
                  {notification.body}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {formatDate(notification.createdAt)}
                </p>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}