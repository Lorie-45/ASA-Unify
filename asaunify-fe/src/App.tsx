import { useEffect, useRef, useState } from 'react';
import AppRoutes from './routes/AppRoutes';
import { useAuthStore } from './store/authStore';
import { useNotificationStore } from './store/notificationStore';
import { useWebSocket } from './hooks/useWebSocket';
import { requestAccessToken } from './api/api';
import { Toaster } from 'sonner';

export default function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const accessToken = useAuthStore((state) => state.accessToken);
  const setAccessToken = useAuthStore((state) => state.setAccessToken);
  const logout = useAuthStore((state) => state.logout);
  const fetchSummary = useNotificationStore((state) => state.fetchSummary);

  // On first load the access token is gone (memory-only). If the persisted
  // session says we're logged in, silently obtain a new one from the httpOnly
  // refresh cookie before rendering protected content.
  const [bootstrapping, setBootstrapping] = useState(
    isAuthenticated && !accessToken
  );
  const didBootstrap = useRef(false);

  useEffect(() => {
    if (didBootstrap.current) return;
    didBootstrap.current = true;

    if (isAuthenticated && !accessToken) {
      requestAccessToken()
        .then((token) => setAccessToken(token))
        .catch(() => logout())
        .finally(() => setBootstrapping(false));
    } else {
      setBootstrapping(false);
    }
  }, [isAuthenticated, accessToken, setAccessToken, logout]);

  useWebSocket();

  useEffect(() => {
    if (isAuthenticated && accessToken) {
      fetchSummary();
    }
  }, [isAuthenticated, accessToken, fetchSummary]);

  if (bootstrapping) {
    return null; // brief blank while the session is restored
  }

  return (
    <>
      <AppRoutes />
      <Toaster position="top-right" richColors />
    </>
  );
}
