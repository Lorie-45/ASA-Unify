import { create } from 'zustand';
import type { NotificationDto } from '../types/notification.types';
import { notificationsApi } from '../api/notifications.api';

interface NotificationState {
  unreadCount: number;
  notifications: NotificationDto[];
  isLoading: boolean;

  // Fetch the summary (unread count + last 10) — called on app load
  fetchSummary: () => Promise<void>;

  // Fetch full notification list — called on the notifications page
  fetchAll: () => Promise<void>;

  // Called when a new notification arrives via WebSocket
  addNotification: (notification: NotificationDto) => void;

  // Mark all as read — optimistic update + API call
  markAllAsRead: () => Promise<void>;

  // Mark single notification as read
  markAsRead: (id: string) => Promise<void>;
}

export const useNotificationStore = create<NotificationState>((set, get) => ({
  unreadCount: 0,
  notifications: [],
  isLoading: false,

  fetchSummary: async () => {
    set({ isLoading: true });
    try {
      const summary = await notificationsApi.getSummary();
      set({
        unreadCount: summary.unreadCount,
        notifications: summary.notifications,
        isLoading: false,
      });
    } catch (error) {
      console.error('Failed to fetch notification summary:', error);
      set({ isLoading: false });
    }
  },

  fetchAll: async () => {
    set({ isLoading: true });
    try {
      const all = await notificationsApi.getAll();
      set({ notifications: all, isLoading: false });
    } catch (error) {
      console.error('Failed to fetch notifications:', error);
      set({ isLoading: false });
    }
  },

  addNotification: (notification) => {
    set((state) => ({
      notifications: [notification, ...state.notifications],
      unreadCount: state.unreadCount + 1,
    }));
  },

  markAllAsRead: async () => {
    const previous = get().notifications;
    // Optimistic update — UI feels instant
    set((state) => ({
      unreadCount: 0,
      notifications: state.notifications.map((n) => ({ ...n, isRead: true })),
    }));
    try {
      await notificationsApi.markAllAsRead();
    } catch (error) {
      console.error('Failed to mark all as read:', error);
      // Roll back on failure
      set({ notifications: previous });
    }
  },

  markAsRead: async (id) => {
    const previous = get().notifications;
    const wasUnread = previous.find((n) => n.id === id && !n.isRead);

    set((state) => ({
      notifications: state.notifications.map((n) =>
        n.id === id ? { ...n, isRead: true } : n
      ),
      unreadCount: wasUnread
        ? Math.max(0, state.unreadCount - 1)
        : state.unreadCount,
    }));

    try {
      await notificationsApi.markAsRead(id);
    } catch (error) {
      console.error('Failed to mark notification as read:', error);
      set({ notifications: previous });
    }
  },
}));