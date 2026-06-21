import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import type { NotificationDto } from '../types/notification.types';

export function useWebSocket() {
  const userId = useAuthStore((state) => state.userId);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const addNotification = useNotificationStore((state) => state.addNotification);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !userId) return;

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(import.meta.env.VITE_WS_URL) as WebSocket,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(
          `/user/${userId}/queue/notifications`,
          (message: IMessage) => {
            try {
              const notification: NotificationDto = JSON.parse(message.body);
              addNotification(notification);
            } catch (error) {
              console.error('Failed to parse notification:', error);
            }
          }
        );
      },
      onStompError: (frame) => {
        console.error('WebSocket STOMP error:', frame);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [isAuthenticated, userId, addNotification]);
}