import { useState } from 'react';
import { X } from 'lucide-react';
import { StageStatus, type StageActionType } from '../../types/enums';
import Button from '../ui/Button';

interface ApprovalActionModalProps {
  isOpen: boolean;
  action: StageActionType | null;
  isLogistics?: boolean;
  onClose: () => void;
  onConfirm: (comment: string, inStock?: boolean) => void;
  isSubmitting: boolean;
}

export default function ApprovalActionModal({
  isOpen,
  action,
  isLogistics,
  onClose,
  onConfirm,
  isSubmitting,
}: ApprovalActionModalProps) {
  const [comment, setComment] = useState('');
  const [inStock, setInStock] = useState(true);

  if (!isOpen || !action) return null;

  const isReject = action === StageStatus.REJECTED;

  function handleConfirm() {
    if (isReject && !comment.trim()) return; 
    onConfirm(comment, isLogistics ? inStock : undefined);
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">
            {isReject ? 'Reject Request' : 'Approve Request'}
          </h3>
          <button onClick={onClose} title='close' className="text-gray-400 hover:text-gray-600">
            <X size={20} />
          </button>
        </div>

        {isLogistics && !isReject && (
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Is this item in stock?
            </label>
            <div className="flex gap-3">
              <button
                onClick={() => setInStock(true)}
                className={`flex-1 py-2 rounded-lg text-sm font-medium border ${
                  inStock
                    ? 'bg-primary text-white border-primary'
                    : 'border-gray-300 text-gray-600'
                }`}
              >
                In Stock
              </button>
              <button
                onClick={() => setInStock(false)}
                className={`flex-1 py-2 rounded-lg text-sm font-medium border ${
                  !inStock
                    ? 'bg-primary text-white border-primary'
                    : 'border-gray-300 text-gray-600'
                }`}
              >
                Out of Stock
              </button>
            </div>
          </div>
        )}

        <label className="block text-sm font-medium text-gray-700 mb-1">
          Comment {isReject && <span className="text-status-rejected">*</span>}
        </label>
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={4}
          placeholder={
            isReject
              ? 'Please explain why this request is being rejected...'
              : 'Optional comment...'
          }
          className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
        />
        {isReject && !comment.trim() && (
          <p className="text-xs text-status-rejected mt-1">
            A comment is required when rejecting
          </p>
        )}

        <div className="flex gap-3 mt-6">
          <Button variant="ghost" onClick={onClose} className="flex-1">
            Cancel
          </Button>
          <Button
            variant={isReject ? 'danger' : 'primary'}
            onClick={handleConfirm}
            disabled={isSubmitting || (isReject && !comment.trim())}
            className="flex-1"
          >
            {isSubmitting ? 'Processing...' : isReject ? 'Reject' : 'Approve'}
          </Button>
        </div>
      </div>
    </div>
  );
}