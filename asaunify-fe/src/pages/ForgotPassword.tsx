import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, ArrowLeft } from 'lucide-react';
import { authApi } from '../api/auth.api';
import { toastApiError, toastError, toastSuccess } from '../utils/toast';
import {
  normalizeEmail,
  validateEmail,
  validatePassword,
  validateResetCode,
} from '../utils/validation';

type Step = 'email' | 'code' | 'password';

export default function ForgotPassword() {
  const navigate = useNavigate();

  const [step, setStep] = useState<Step>('email');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Step 1 — request a code.
  async function handleRequestCode(e: React.FormEvent) {
    e.preventDefault();

    const emailError = validateEmail(email);
    if (emailError) {
      toastError(emailError);
      return;
    }

    setIsLoading(true);
    try {
      const { message } = await authApi.forgotPassword(normalizeEmail(email));
      // The backend answers identically whether or not the account exists, so
      // we always advance. Showing "no such account" here would hand an
      // attacker the account enumeration the API is built to prevent.
      toastSuccess(message);
      setStep('code');
    } catch (err) {
      toastApiError(err, 'Could not process the request. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  // Step 2 — check the code before asking for a new password. This does not
  // spend the code; it is sent again with the reset itself.
  async function handleVerifyCode(e: React.FormEvent) {
    e.preventDefault();

    const codeError = validateResetCode(code);
    if (codeError) {
      toastError(codeError);
      return;
    }

    setIsLoading(true);
    try {
      await authApi.verifyResetCode(normalizeEmail(email), code.trim());
      setStep('password');
    } catch (err) {
      toastApiError(err, 'Could not verify the code. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }

  // Step 3 — set the new password.
  async function handleResetPassword(e: React.FormEvent) {
    e.preventDefault();

    const passwordError = validatePassword(newPassword);
    if (passwordError) {
      toastError(passwordError);
      return;
    }
    if (newPassword !== confirmPassword) {
      toastError('Passwords do not match');
      return;
    }

    setIsLoading(true);
    try {
      await authApi.resetPassword(
        normalizeEmail(email),
        code.trim(),
        newPassword
      );
      toastSuccess('Password updated. Please log in with your new password.');
      navigate('/login');
    } catch (err) {
      // An expired or exhausted code lands here — send the user back to the
      // start so they can request a fresh one.
      toastApiError(err, 'Could not reset the password. Please try again.');
      setStep('email');
      setCode('');
      setNewPassword('');
      setConfirmPassword('');
    } finally {
      setIsLoading(false);
    }
  }

  const inputClass =
    'w-full px-4 py-3 border border-gray-300 rounded-lg text-sm ' +
    'focus:outline-none focus:ring-2 focus:ring-primary/30';

  const buttonClass =
    'w-full bg-primary text-white py-3 rounded-xl font-semibold ' +
    'hover:bg-primary-dark transition-colors disabled:opacity-60 mt-6';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 shadow-sm bg-white p-10">
        <h2 className="text-2xl font-bold text-teal mb-2">Reset your password</h2>

        {step === 'email' && (
          <>
            <p className="text-sm text-gray-500 mb-8">
              Enter your work email and we will send you a 6-digit
              verification code.
            </p>
            <form onSubmit={handleRequestCode} className="space-y-4">
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Email"
                autoComplete="email"
                autoFocus
                className={inputClass}
              />
              <button type="submit" disabled={isLoading} className={buttonClass}>
                {isLoading ? 'Sending...' : 'Send code'}
              </button>
            </form>
          </>
        )}

        {step === 'code' && (
          <>
            <p className="text-sm text-gray-500 mb-8">
              If an account exists for{' '}
              <span className="font-medium text-gray-700">
                {normalizeEmail(email)}
              </span>
              , a 6-digit code is on its way. It expires in 10 minutes.
            </p>
            <form onSubmit={handleVerifyCode} className="space-y-4">
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={code}
                onChange={(e) =>
                  setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                }
                placeholder="6-digit code"
                autoComplete="one-time-code"
                autoFocus
                className={`${inputClass} tracking-[0.5em] text-center text-lg`}
              />
              <button type="submit" disabled={isLoading} className={buttonClass}>
                {isLoading ? 'Verifying...' : 'Verify code'}
              </button>
            </form>
            <button
              type="button"
              onClick={() => setStep('email')}
              className="text-sm text-primary font-medium hover:underline mt-4"
            >
              Use a different email
            </button>
          </>
        )}

        {step === 'password' && (
          <>
            <p className="text-sm text-gray-500 mb-8">
              Choose a new password. It must be at least 6 characters and
              include an uppercase letter, a lowercase letter, a number and a
              special character.
            </p>
            <form onSubmit={handleResetPassword} className="space-y-4">
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="New password"
                  autoComplete="new-password"
                  autoFocus
                  className={`${inputClass} pr-11`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  title={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              <input
                type={showPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Confirm new password"
                autoComplete="new-password"
                className={inputClass}
              />
              <button type="submit" disabled={isLoading} className={buttonClass}>
                {isLoading ? 'Updating...' : 'Update password'}
              </button>
            </form>
          </>
        )}

        <Link
          to="/login"
          className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mt-8"
        >
          <ArrowLeft size={16} />
          Back to login
        </Link>
      </div>
    </div>
  );
}
