import type { AuthRequest, AuthResponse } from '../types/user.types';
import api from './api';

// The password-reset endpoints return only a human-readable message — never a
// code, token, or any hint about whether the account exists.
interface MessageResponse {
  message: string;
}

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

  // ─── Password reset ────────────────────────────────────────

  // Always resolves for a well-formed address, whether or not an account
  // exists — the backend deliberately gives no way to tell the difference.
  forgotPassword: async (email: string): Promise<MessageResponse> => {
    const response = await api.post<MessageResponse>(
      '/auth/forgot-password',
      { email }
    );
    return response.data;
  },

  // Checks the code without spending it; it is still required at reset time.
  verifyResetCode: async (
    email: string,
    code: string
  ): Promise<MessageResponse> => {
    const response = await api.post<MessageResponse>(
      '/auth/verify-reset-code',
      { email, code }
    );
    return response.data;
  },

  resetPassword: async (
    email: string,
    code: string,
    newPassword: string
  ): Promise<MessageResponse> => {
    const response = await api.post<MessageResponse>(
      '/auth/reset-password',
      { email, code, newPassword }
    );
    return response.data;
  },
};
