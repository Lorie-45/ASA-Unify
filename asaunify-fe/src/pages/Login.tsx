import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { authApi } from '../api/auth.api';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import axios from 'axios';
import { toastApiError, toastError, toastSuccess } from '../utils/toast';

export default function Login() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const fetchSummary = useNotificationStore((state) => state.fetchSummary);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    if (!email || !password) {
      toastError('Please enter both email and password');
      return;
    }

    setIsLoading(true);
    try {
      const response = await authApi.login({ email, password });

      setAuth({
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        userId: response.userId,
        fullName: response.fullName,
        email: response.email,
        role: response.role,
        departmentName: response.departmentName,
      });

      // Hydrate notification bell right after login
      fetchSummary();
    toastSuccess(`Welcome back, ${response.fullName}!`);
      navigate('/dashboard');
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        toastError('Invalid email or password');
      } else {
        toastApiError(err)
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <div className="w-full max-w-4xl rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex bg-white">
        {/* Left panel — brand gradient */}
        <div className="hidden md:flex w-1/2 relative bg-gradient-to-br from-teal-dark via-teal to-primary items-center justify-center p-12 overflow-hidden">
          {/* Diagonal accent shapes */}
          <div className="absolute inset-0 opacity-90 bg-gradient-to-tr from-teal-dark via-teal to-primary" />
          <div className="relative z-10 flex flex-col items-center text-white text-center">
            <img
              src="/Asa-logo-white.png"
              alt="ASA International"
              className="h-30 mb-4"
            />
          </div>
        </div>

        {/* Right panel — form */}
        <div className="w-full md:w-1/2 p-12 flex flex-col justify-center">
          <h2 className="text-2xl font-bold text-teal mb-10">
            Login to ASAUnify
          </h2>

            <form onSubmit={handleSubmit} className="space-y-4">
                <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Email"
                autoComplete="email"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />

                <div className="relative">
                <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Password"
                    autoComplete="current-password"
                    className="w-full px-4 py-3 pr-11 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                >
                    {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
                </div>

                <div>
                <a href="#" className="text-sm text-primary font-medium hover:underline" >                
                    Forgot Password?
                </a>
                </div>

                <button
                type="submit"
                disabled={isLoading}
                className="w-full bg-primary text-white py-3 rounded-xl font-semibold hover:bg-primary-dark transition-colors disabled:opacity-60 mt-6"
                >
                {isLoading ? 'Logging in...' : 'Login'}
                </button>
            </form>
        </div>
      </div>
    </div>
  );
}