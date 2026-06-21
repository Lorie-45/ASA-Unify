import type {
  NotificationDto,
  NotificationSummaryDto,
} from '../types/notification.types';
import api from './api';

export const notificationsApi = {
  getSummary: async (): Promise<NotificationSummaryDto> => {
    const response = await api.get<NotificationSummaryDto>(
      '/notifications/summary'
    );
    return response.data;
  },

  getAll: async (): Promise<NotificationDto[]> => {
    const response = await api.get<NotificationDto[]>(
      '/notifications'
    );
    return response.data;
  },

  markAllAsRead: async (): Promise<void> => {
    await api.post('/notifications/read-all');
  },

  markAsRead: async (id: string): Promise<void> => {
    await api.post(`/notifications/${id}/read`);
  },
};