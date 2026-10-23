import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import type { NotificationDto } from '../types/notification.types';

export function useWebSocket() {
  const userId = useAuthStore((state) => state.userId);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  // Connect only once an access token exists (it is restored asynchronously
  // after a page reload). The token itself is read fresh on every (re)connect.
  const hasToken = useAuthStore((state) => !!state.accessToken);
  const addNotification = useNotificationStore((state) => state.addNotification);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !userId || !hasToken) return;

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(import.meta.env.VITE_WS_URL) as WebSocket,
      reconnectDelay: 5000,
      // The server authenticates the STOMP session from this header.
      beforeConnect: () => {
        client.connectHeaders = {
          Authorization: `Bearer ${useAuthStore.getState().accessToken ?? ''}`,
        };
      },
      onConnect: () => {
        // Spring resolves /user/queue/... to this session's own queue.
        client.subscribe(
          '/user/queue/notifications',
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
  }, [isAuthenticated, userId, hasToken, addNotification]);
}