export interface User {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface AuthResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  accessToken: string;
  tokenType: string;
}

export interface Account {
  id: string;
  accountNumber: string;
  balance: number;
  currency: string;
  status: string;
}

export interface Transaction {
  id: string;
  amount: number;
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  fromAccountNumber?: string;
  toAccountNumber?: string;
  targetBankCode?: string;
  targetAccountName?: string;
  description?: string;
  createdAt: string;
}

export interface BeneficiaryInfo {
  accountName: string;
  accountNumber: string;
  bankCode: string;
  bankName: string;
  nipSessionId: string;
}
