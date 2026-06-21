import { useEffect } from 'react';
import AppRoutes from './routes/AppRoutes';
import { useAuthStore } from './store/authStore';
import { useNotificationStore } from './store/notificationStore';
import { useWebSocket } from './hooks/useWebSocket';

export default function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const fetchSummary = useNotificationStore((state) => state.fetchSummary);

  useWebSocket();

  useEffect(() => {
    // Hydrate notification bell on app load if already logged in
    // (e.g. user refreshed the page with a valid persisted session)
    if (isAuthenticated) {
      fetchSummary();
    }
  }, [isAuthenticated, fetchSummary]);

  return <AppRoutes />;
}