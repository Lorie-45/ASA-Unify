import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Role } from '../types/enums';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: string | null;
  fullName: string | null;
  email: string | null;
  role: Role | null;
  departmentName: string | null;
  isAuthenticated: boolean;

  setAuth: (data: {
    accessToken: string;
    refreshToken: string;
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
      refreshToken: null,
      userId: null,
      fullName: null,
      email: null,
      role: null,
      departmentName: null,
      isAuthenticated: false,

      setAuth: (data) =>
        set({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
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
          refreshToken: null,
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
    }
  )
);