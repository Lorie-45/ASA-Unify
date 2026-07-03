import { useEffect } from 'react';
import AppRoutes from './routes/AppRoutes';
import { useAuthStore } from './store/authStore';
import { useNotificationStore } from './store/notificationStore';
import { useWebSocket } from './hooks/useWebSocket';
import { Toaster } from 'sonner';

export default function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const fetchSummary = useNotificationStore((state) => state.fetchSummary);

  useWebSocket();

  useEffect(() => {
    if (isAuthenticated) {
      fetchSummary();
    }
  }, [isAuthenticated, fetchSummary]);

  return(
    <>
    <AppRoutes />
    <Toaster position='top-right' richColors />
    </>
  );
}