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

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }
};
