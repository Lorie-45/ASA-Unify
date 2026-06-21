import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'outline' | 'danger' | 'ghost';
  icon?: ReactNode;
  children: ReactNode;
}

const VARIANT_CLASSES: Record<string, string> = {
  primary: 'bg-primary text-white hover:bg-primary-dark',
  outline: 'border border-primary text-primary hover:bg-primary/5',
  danger: 'bg-status-rejected text-white hover:bg-red-700',
  ghost: 'text-gray-600 hover:bg-gray-100',
};

export default function Button({
  variant = 'primary',
  icon,
  children,
  className = '',
  ...rest
}: ButtonProps) {
  return (
    <button
      className={`flex items-center gap-2 px-5 py-2.5 rounded-pill text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${VARIANT_CLASSES[variant]} ${className}`}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}