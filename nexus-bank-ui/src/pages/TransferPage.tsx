import React, { useState } from 'react';
import { transactionService } from '../services/transactionService';
import type { BeneficiaryInfo } from '../types';
import { 
  Search, 
  SendHorizontal, 
  User, 
  Banknote, 
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  Loader2
} from 'lucide-react';

const BANKS = [
  { code: "000", name: "Nexus Bank" },
  { code: "011", name: "First Bank of Nigeria" },
  { code: "058", name: "Guaranty Trust Bank" },
  { code: "057", name: "Zenith Bank" },
  { code: "044", name: "Access Bank" },
  { code: "033", name: "United Bank for Africa" },
  { code: "090267", name: "Kuda Bank" }
];

const TransferPage: React.FC = () => {
  const [step, setStep] = useState(1);
  const [bankCode, setBankCode] = useState('000');
  const [accountNumber, setAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  
  const [beneficiary, setBeneficiary] = useState<BeneficiaryInfo | null>(null);
  const [isResolving, setIsResolving] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [, setSuccess] = useState(false);

  const handleResolve = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsResolving(true);
    try {
      const result = await transactionService.resolveBeneficiary(bankCode, accountNumber);
      setBeneficiary(result);
      setStep(2);
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Could not resolve account details. Please check the number.');
    } finally {
      setIsResolving(false);
    }
  };

  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);
    try {
      await transactionService.transfer({
        amount: parseFloat(amount),
        toAccountNumber: accountNumber,
        targetBankCode: bankCode,
        targetAccountName: beneficiary?.accountName,
        description,
        idempotencyKey: crypto.randomUUID()
      });
      setSuccess(true);
      setStep(3);
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Transfer failed. Please check your balance.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-navy-dark text-center">Inter-bank Transfer</h1>
        <p className="text-gray-500 text-center">Send funds securely via NIBSS NIP</p>
      </div>

      <div className="bg-white rounded-2xl shadow-xl border border-gray-100 overflow-hidden">
        {/* Progress Bar */}
        <div className="flex h-2 bg-gray-100">
          <div className={`transition-all duration-500 bg-gold ${step >= 1 ? 'w-1/3' : 'w-0'}`} />
          <div className={`transition-all duration-500 bg-gold ${step >= 2 ? 'w-1/3' : 'w-0'}`} />
          <div className={`transition-all duration-500 bg-gold ${step >= 3 ? 'w-1/3' : 'w-0'}`} />
        </div>

        <div className="p-10">
          {error && (
            <div className="mb-6 bg-red-50 border-l-4 border-red-500 p-4 flex items-center space-x-3">
              <AlertCircle className="text-red-500 w-5 h-5 flex-shrink-0" />
              <span className="text-red-700 text-sm font-medium">{error}</span>
            </div>
          )}

          {step === 1 && (
            <form onSubmit={handleResolve} className="space-y-6">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-bold text-navy-dark mb-2">Select Destination Bank</label>
                  <select 
                    className="w-full p-3 border border-gray-300 rounded-lg outline-none focus:ring-2 focus:ring-gold appearance-none bg-white"
                    value={bankCode}
                    onChange={(e) => setBankCode(e.target.value)}
                  >
                    {BANKS.map(b => <option key={b.code} value={b.code}>{b.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-bold text-navy-dark mb-2">Account Number</label>
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-5 h-5" />
                    <input
                      type="text"
                      maxLength={10}
                      required
                      className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg outline-none focus:ring-2 focus:ring-gold"
                      placeholder="0123456789"
                      value={accountNumber}
                      onChange={(e) => setAccountNumber(e.target.value)}
                    />
                  </div>
                </div>
              </div>
              <button
                type="submit"
                disabled={isResolving || accountNumber.length < 10}
                className="w-full bg-navy text-white font-bold py-4 rounded-xl shadow-lg hover:bg-navy-light transition-all flex items-center justify-center space-x-2 disabled:opacity-50"
              >
                {isResolving ? <Loader2 className="animate-spin w-5 h-5" /> : <span>Resolve Beneficiary</span>}
                {!isResolving && <ArrowRight className="w-5 h-5" />}
              </button>
            </form>
          )}

          {step === 2 && beneficiary && (
            <form onSubmit={handleTransfer} className="space-y-6">
              <div className="bg-gray-50 rounded-xl p-6 border border-navy/5 flex items-center space-x-4">
                <div className="w-12 h-12 bg-navy rounded-full flex items-center justify-center text-gold">
                  <User className="w-6 h-6" />
                </div>
                <div>
                  <div className="text-xs font-bold text-gray-400 uppercase tracking-widest">Recipient Name</div>
                  <div className="text-lg font-bold text-navy-dark">{beneficiary.accountName}</div>
                  <div className="text-sm text-navy-light font-medium">{beneficiary.bankName} • {beneficiary.accountNumber}</div>
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-bold text-navy-dark mb-2">Amount (NGN)</label>
                  <div className="relative">
                    <Banknote className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-5 h-5" />
                    <input
                      type="number"
                      step="0.01"
                      required
                      min="100"
                      className="w-full pl-10 pr-4 py-3 border border-gray-300 rounded-lg outline-none focus:ring-2 focus:ring-gold text-2xl font-mono"
                      placeholder="0.00"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-bold text-navy-dark mb-2">Description (Optional)</label>
                  <input
                    type="text"
                    className="w-full p-3 border border-gray-300 rounded-lg outline-none focus:ring-2 focus:ring-gold"
                    placeholder="Transaction narration"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                </div>
              </div>

              <div className="flex space-x-4">
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="flex-1 border-2 border-gray-200 text-gray-500 font-bold py-4 rounded-xl hover:bg-gray-50 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="flex-[2] bg-gold text-navy font-bold py-4 rounded-xl shadow-lg hover:bg-gold-light transition-all flex items-center justify-center space-x-2 disabled:opacity-50"
                >
                  {isSubmitting ? <Loader2 className="animate-spin w-5 h-5" /> : (
                    <>
                      <SendHorizontal className="w-5 h-5" />
                      <span>Confirm Transfer</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}

          {step === 3 && (
            <div className="text-center py-8 space-y-6">
              <div className="inline-flex items-center justify-center w-20 h-20 bg-green-100 rounded-full text-green-600 mb-2">
                <CheckCircle2 className="w-10 h-10" />
              </div>
              <div>
                <h2 className="text-2xl font-bold text-navy-dark">Transaction Initiated</h2>
                <p className="text-gray-500 mt-2 px-10">
                  Your transfer of <b>NGN {parseFloat(amount).toLocaleString()}</b> to <b>{beneficiary?.accountName}</b> is being processed via NIBSS.
                </p>
              </div>
              <button
                onClick={() => window.location.href = '/dashboard'}
                className="bg-navy text-white font-bold px-8 py-3 rounded-lg hover:bg-navy-light transition-all"
              >
                Back to Dashboard
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default TransferPage;
