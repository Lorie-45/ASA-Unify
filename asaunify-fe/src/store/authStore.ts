import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Role } from '../types/enums';

interface AuthState {
  // Access token is held in memory ONLY (never persisted) so it cannot be
  // stolen from localStorage via XSS. On reload it is re-obtained via the
  // httpOnly refresh cookie (silent refresh in App).
  accessToken: string | null;
  userId: string | null;
  fullName: string | null;
  email: string | null;
  role: Role | null;
  departmentName: string | null;
  isAuthenticated: boolean;

  setAuth: (data: {
    accessToken: string;
    userId: string;
    fullName: string;
    email: string;
    role: Role;
    departmentName: string | null;
  }) => void;
  setAccessToken: (token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      userId: null,
      fullName: null,
      email: null,
      role: null,
      departmentName: null,
      isAuthenticated: false,

      setAuth: (data) =>
        set({
          accessToken: data.accessToken,
          userId: data.userId,
          fullName: data.fullName,
          email: data.email,
          role: data.role,
          departmentName: data.departmentName,
          isAuthenticated: true,
        }),

      setAccessToken: (token) => set({ accessToken: token }),

      logout: () =>
        set({
          accessToken: null,
          userId: null,
          fullName: null,
          email: null,
          role: null,
          departmentName: null,
          isAuthenticated: false,
        }),
    }),
    {
      name: 'asaunify-auth',
      // Persist only non-sensitive identity fields — NEVER the access token.
      partialize: (state) => ({
        userId: state.userId,
        fullName: state.fullName,
        email: state.email,
        role: state.role,
        departmentName: state.departmentName,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
