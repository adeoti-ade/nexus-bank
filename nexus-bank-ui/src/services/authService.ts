import api from './api';
import type { AuthResponse } from '../types';

export const authService = {
  async login(request: any): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/login', request);
    return response.data;
  },

  async register(request: any): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/register', request);
    return response.data;
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch {
      // Server-side revocation is best-effort; always clear local state
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    }
  }
};
