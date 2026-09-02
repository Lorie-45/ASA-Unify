import axios, { type InternalAxiosRequestConfig, type AxiosError } from "axios";
import { useAuthStore } from "../store/authStore";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  // Send/receive the httpOnly refresh-token cookie on auth calls.
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

// ─── Request Interceptor — attach in-memory access token ───

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ─── Response Interceptor — refresh access token on 401 ────
// The refresh token lives in an httpOnly cookie; we never read it in JS.
// A 401 triggers a single refresh call (others queue behind it).

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

function onTokenRefreshed(token: string) {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

export async function requestAccessToken(): Promise<string> {
  const response = await axios.post(
    `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
    {},
    { withCredentials: true },
  );
  return response.data.accessToken as string;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // Never run the refresh flow for auth endpoints themselves (login /
    // logout / refresh) — a 401 there is a real failure, not an expired token.
    const isAuthCall = originalRequest?.url?.includes("/auth/");

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthCall) {
      originalRequest._retry = true;

      if (!isRefreshing) {
        isRefreshing = true;
        try {
          const newAccessToken = await requestAccessToken();
          useAuthStore.getState().setAccessToken(newAccessToken);
          isRefreshing = false;
          onTokenRefreshed(newAccessToken);
        } catch (refreshError) {
          isRefreshing = false;
          useAuthStore.getState().logout();
          window.location.href = "/login";
          return Promise.reject(refreshError);
        }
      }

      // Queue this request until the token refresh completes.
      return new Promise((resolve) => {
        refreshSubscribers.push((token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(api(originalRequest));
        });
      });
    }

    return Promise.reject(error);
  },
);

export default api;
