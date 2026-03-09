import React, { useEffect, useState } from 'react';
import { accountService } from '../services/accountService';
import { transactionService } from '../services/transactionService';
import type { Account, Transaction } from '../types';
import { Wallet, ArrowUpRight, ArrowDownLeft, Clock, CheckCircle2, XCircle, AlertCircle } from 'lucide-react';

const DashboardPage: React.FC = () => {
  const [account, setAccount] = useState<Account | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [acc, history] = await Promise.all([
          accountService.getMyAccount(),
          transactionService.getHistory()
        ]);
        setAccount(acc);
        setTransactions(history.slice(0, 5)); // Only show top 5
      } catch (err) {
        console.error('Failed to fetch dashboard data', err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED': return <CheckCircle2 className="text-green-500 w-5 h-5" />;
      case 'PROCESSING': return <Clock className="text-blue-500 w-5 h-5 animate-pulse" />;
      case 'FAILED': return <XCircle className="text-red-500 w-5 h-5" />;
      default: return <AlertCircle className="text-gray-400 w-5 h-5" />;
    }
  };

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-gold"></div>
    </div>
  );

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-navy-dark">Account Overview</h1>
        <p className="text-gray-500">Welcome back to your secure dashboard.</p>
      </div>

      {/* Account Card */}
      <div className="bg-navy rounded-2xl p-8 text-white shadow-2xl relative overflow-hidden group">
        <div className="absolute top-0 right-0 p-8 opacity-10 group-hover:scale-110 transition-transform duration-500">
          <Wallet size={120} />
        </div>
        <div className="relative z-10">
          <div className="text-gold-light text-sm font-bold uppercase tracking-widest mb-2">Available Balance</div>
          <div className="text-5xl font-extrabold mb-8 font-mono">
            {account?.currency} {account?.balance.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </div>
          <div className="flex justify-between items-end">
            <div>
              <div className="text-navy-light text-xs font-bold uppercase tracking-wider">Account Number</div>
              <div className="text-xl font-semibold tracking-widest">{account?.accountNumber}</div>
            </div>
            <div className="bg-white/10 px-4 py-2 rounded-lg backdrop-blur-md border border-white/20">
              <span className="text-xs font-bold uppercase tracking-tighter opacity-70">Status</span>
              <div className="text-sm font-bold text-green-400">{account?.status}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Activity */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="p-6 border-b border-gray-50 flex justify-between items-center">
          <h2 className="text-lg font-bold text-navy-dark">Recent Activity</h2>
          <button className="text-navy font-bold text-sm hover:text-gold transition-colors">View All</button>
        </div>
        <div className="divide-y divide-gray-50">
          {transactions.length === 0 ? (
            <div className="p-12 text-center text-gray-400">
              No recent transactions found.
            </div>
          ) : (
            transactions.map((tx) => (
              <div key={tx.id} className="p-6 flex items-center justify-between hover:bg-gray-50 transition-colors">
                <div className="flex items-center space-x-4">
                  <div className={`p-3 rounded-full ${
                    tx.type === 'DEPOSIT' ? 'bg-green-100' : 'bg-orange-100'
                  }`}>
                    {tx.type === 'DEPOSIT' 
                      ? <ArrowDownLeft className={tx.type === 'DEPOSIT' ? 'text-green-600' : 'text-orange-600'} />
                      : <ArrowUpRight className="text-orange-600" />
                    }
                  </div>
                  <div>
                    <div className="font-bold text-navy-dark">
                      {tx.type === 'TRANSFER' ? `Transfer to ${tx.targetAccountName || tx.toAccountNumber}` : tx.type}
                    </div>
                    <div className="text-xs text-gray-400 font-medium">
                      {new Date(tx.createdAt).toLocaleDateString()} at {new Date(tx.createdAt).toLocaleTimeString()}
                    </div>
                  </div>
                </div>
                <div className="flex items-center space-x-6">
                  <div className={`text-lg font-bold font-mono ${
                    tx.type === 'DEPOSIT' ? 'text-green-600' : 'text-navy-dark'
                  }`}>
                    {tx.type === 'DEPOSIT' ? '+' : '-'} {tx.amount.toLocaleString()}
                  </div>
                  <div title={tx.status}>
                    {getStatusIcon(tx.status)}
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
