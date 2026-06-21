import type { ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: number | string;
  subtitle: string;
  variant?: 'filled' | 'outline';
  onClick?: () => void;
  icon?: ReactNode;
}

export default function StatCard({
  title,
  value,
  subtitle,
  variant = 'outline',
  onClick,
}: StatCardProps) {
  const isFilled = variant === 'filled';

  return (
    <div
      onClick={onClick}
      className={`flex-1 min-w-55 rounded-xl p-5 cursor-pointer transition-shadow hover:shadow-md ${
        isFilled
          ? 'bg-primary text-white'
          : 'bg-white border border-teal text-gray-900'
      }`}
    >
      <div className="flex items-center justify-between mb-3">
        <h3
          className={`text-base font-semibold ${
            isFilled ? 'text-white' : 'text-teal'
          }`}
        >
          {title}
        </h3>
        {onClick && (
          <ExternalLink
            size={16}
            className={isFilled ? 'text-white/80' : 'text-gray-400'}
          />
        )}
      </div>
      <p className="text-4xl font-bold mb-2">{value}</p>
      <p
        className={`text-sm ${isFilled ? 'text-white/80' : 'text-gray-500'}`}
      >
        {subtitle}
      </p>
    </div>
  );
}