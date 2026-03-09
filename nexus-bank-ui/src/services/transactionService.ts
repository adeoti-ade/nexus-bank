import api from './api';
import type { Transaction, BeneficiaryInfo } from '../types';

export const transactionService = {
  async getHistory(): Promise<Transaction[]> {
    const response = await api.get<Transaction[]>('/transactions/history');
    return response.data;
  },

  async resolveBeneficiary(bankCode: String, accountNumber: String): Promise<BeneficiaryInfo> {
    const response = await api.get<BeneficiaryInfo>('/external/nibss/resolve', {
      params: { bankCode, accountNumber }
    });
    return response.data;
  },

  async transfer(request: any): Promise<Transaction> {
    const response = await api.post<Transaction>('/transactions/transfer', request);
    return response.data;
  },

  async deposit(request: any): Promise<Transaction> {
    const response = await api.post<Transaction>('/transactions/deposit', request);
    return response.data;
  },

  async withdraw(request: any): Promise<Transaction> {
    const response = await api.post<Transaction>('/transactions/withdraw', request);
    return response.data;
  }
};
