import api from './api';
import type { Account } from '../types';

export const accountService = {
  async getMyAccount(): Promise<Account> {
    const response = await api.get<Account>('/accounts/me');
    return response.data;
  }
};
