import { Search, SlidersHorizontal } from 'lucide-react';

interface SearchFilterBarProps {
  value: string;
  onChange: (value: string) => void;
  onFilterClick?: () => void;
  placeholder?: string;
}

export default function SearchFilterBar({
  value,
  onChange,
  onFilterClick,
  placeholder = 'Search file',
}: SearchFilterBarProps) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 relative">
        <Search
          size={18}
          className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400"
        />
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="w-full pl-11 pr-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
      </div>
      <button
        onClick={onFilterClick}
        className="flex items-center gap-2 px-5 py-3 bg-primary text-white rounded-xl text-sm font-semibold hover:bg-primary-dark transition-colors"
      >
        <SlidersHorizontal size={16} />
        Filters
      </button>
    </div>
  );
}