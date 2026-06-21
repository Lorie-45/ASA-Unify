interface LoanFieldsProps {
  amount: number;
  isTopUp: boolean;
  parentRequestId: string;
  onAmountChange: (value: number) => void;
  onIsTopUpChange: (value: boolean) => void;
  onParentRequestIdChange: (value: string) => void;
}

export default function LoanFields({
  amount,
  isTopUp,
  parentRequestId,
  onAmountChange,
  onIsTopUpChange,
  onParentRequestIdChange,
}: LoanFieldsProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <input
          type="checkbox"
          id="isTopUp"
          checked={isTopUp}
          onChange={(e) => onIsTopUpChange(e.target.checked)}
          className="w-4 h-4 accent-primary"
        />
        <label htmlFor="isTopUp" className="text-sm font-medium text-gray-700">
          This is a top-up on an existing MSME Silver loan
        </label>
      </div>

      {isTopUp && (
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Original Loan Reference
          </label>
          <input
            type="text"
            value={parentRequestId}
            onChange={(e) => onParentRequestIdChange(e.target.value)}
            placeholder="e.g. CN-005"
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>
      )}

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          Loan Amount (RWF)
        </label>
        <input
          type="number"
          min={0}
          value={amount}
          onChange={(e) => onAmountChange(Number(e.target.value))}
          placeholder="e.g. 3000000"
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
        <p className="text-xs text-gray-400 mt-1">
          {isTopUp
            ? 'Maximum top-up increment: 1,500,000 RWF'
            : 'MSME Silver: 2,000,001 – 5,000,000 RWF · MSME Gold: 5,000,001 – 10,000,000 RWF'}
        </p>
      </div>
    </div>
  );
}