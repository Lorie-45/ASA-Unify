import type { AuthRequest, AuthResponse } from '../types/user.types';
import api from './api';

export const authApi = {
  login: async (credentials: AuthRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>(
      '/auth/login',
      credentials
    );
    return response.data;
  },

  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },

  // Refresh token is sent automatically via the httpOnly cookie.
  refresh: async (): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/auth/refresh', {});
    return response.data;
  },
};
