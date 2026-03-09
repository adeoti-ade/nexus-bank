import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { authService } from '../services/authService';
import { Landmark, User, Mail, Lock, Loader2 } from 'lucide-react';

const RegisterPage: React.FC = () => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: ''
  });
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      const response = await authService.register(formData);
      login(response);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Registration failed. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  return (
    <div className="min-h-screen bg-navy-dark flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow-2xl overflow-hidden my-8">
        <div className="bg-navy p-8 text-center">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-gold rounded-full mb-4">
            <Landmark className="text-navy w-8 h-8" />
          </div>
          <h1 className="text-2xl font-bold text-white uppercase tracking-wider">Join Nexus Bank</h1>
          <p className="text-navy-light mt-2 text-sm font-medium">Create your secure digital account</p>
        </div>
        
        <form onSubmit={handleSubmit} className="p-8 space-y-5">
          {error && (
            <div className="bg-red-50 border-l-4 border-red-500 p-4 text-red-700 text-sm">
              {error}
            </div>
          )}
          
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-navy-dark mb-2">First Name</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
                <input
                  name="firstName"
                  type="text"
                  required
                  className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-md focus:ring-2 focus:ring-gold focus:border-transparent outline-none transition-all text-sm"
                  placeholder="John"
                  value={formData.firstName}
                  onChange={handleChange}
                />
              </div>
            </div>
            <div>
              <label className="block text-sm font-semibold text-navy-dark mb-2">Last Name</label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
                <input
                  name="lastName"
                  type="text"
                  required
                  className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-md focus:ring-2 focus:ring-gold focus:border-transparent outline-none transition-all text-sm"
                  placeholder="Doe"
                  value={formData.lastName}
                  onChange={handleChange}
                />
              </div>
            </div>
          </div>

          <div>
            <label className="block text-sm font-semibold text-navy-dark mb-2">Email Address</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
              <input
                name="email"
                type="email"
                required
                className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-md focus:ring-2 focus:ring-gold focus:border-transparent outline-none transition-all text-sm"
                placeholder="john.doe@example.com"
                value={formData.email}
                onChange={handleChange}
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-semibold text-navy-dark mb-2">Password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
              <input
                name="password"
                type="password"
                required
                className="w-full pl-9 pr-4 py-2.5 border border-gray-300 rounded-md focus:ring-2 focus:ring-gold focus:border-transparent outline-none transition-all text-sm"
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full bg-navy hover:bg-navy-light text-white font-bold py-3 rounded-md shadow-lg transform active:scale-[0.98] transition-all flex items-center justify-center space-x-2 disabled:opacity-70 mt-2"
          >
            {isSubmitting ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <span>Create Account</span>
            )}
          </button>

          <div className="text-center text-sm text-gray-600">
            Already have an account?{' '}
            <Link to="/login" className="text-navy font-bold hover:text-gold transition-colors">
              Sign In
            </Link>
          </div>
        </form>
        
        <div className="bg-gray-50 px-8 py-4 text-center border-t border-gray-100">
          <p className="text-xs text-gray-400 uppercase tracking-widest font-semibold">
            Licensed by Central Bank
          </p>
        </div>
      </div>
    </div>
  );
};

export default RegisterPage;
