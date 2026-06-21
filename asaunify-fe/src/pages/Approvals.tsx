import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { requestsApi } from '../api/requests.api';
import { useAuthStore } from '../store/authStore';
import SearchFilterBar from '../components/ui/SearchFilterBar';
import ApprovalActionModal from '../components/requests/ApprovalActionModal';
import { Role, StageStatus, type StageActionType } from '../types/enums';
import { formatDate } from '../utils/formatDate';
import type { RequestResponseDto } from '../types/request.types';

export default function Approvals() {
  const navigate = useNavigate();
  const role = useAuthStore((state) => state.role);

  const [requests, setRequests] = useState<RequestResponseDto[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [activeRequest, setActiveRequest] = useState<RequestResponseDto | null>(null);
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadPending = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await requestsApi.getPendingForRole();
      setRequests(data);
    } catch (error) {
      console.error('Failed to load pending approvals:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadPending();
  }, [loadPending]);

  function openAction(request: RequestResponseDto, action: StageActionType) {
    setActiveRequest(request);
    setModalAction(action);
  }

  async function handleConfirm(comment: string, inStock?: boolean) {
    if (!activeRequest || !modalAction) return;
    setIsSubmitting(true);
    try {
      await requestsApi.processAction(activeRequest.id, {
        action: modalAction,
        comment: comment || undefined,
        inStock,
      });
      setModalAction(null);
      setActiveRequest(null);
      loadPending();
    } catch (error) {
      console.error('Failed to process action:', error);
    } finally {
      setIsSubmitting(false);
    }
  }

  const filtered = requests.filter(
    (r) =>
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.referenceNumber.toLowerCase().includes(search.toLowerCase()) ||
      r.initiatorName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold text-gray-900">Approvals</h1>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search by name, reference..."
      />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : filtered.length === 0 ? (
        <div className="border border-dashed border-gray-200 rounded-xl p-12 text-center text-gray-400 text-sm">
          Nothing waiting on your approval right now.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((request) => (
            <ApprovalCard
              key={request.id}
              request={request}
              isLogistics={role === Role.LOGISTICS}
              onViewDetails={() => navigate(`/requests/${request.id}`)}
              onApprove={() => openAction(request, StageStatus.APPROVED)}
              onReject={() => openAction(request, StageStatus.REJECTED)}
            />
          ))}
        </div>
      )}

      <ApprovalActionModal
        isOpen={modalAction !== null}
        action={modalAction}
        isLogistics={role === Role.LOGISTICS}
        onClose={() => {
          setModalAction(null);
          setActiveRequest(null);
        }}
        onConfirm={handleConfirm}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}

// ─── Card ─────────────────────────────────────────────────

function ApprovalCard({
  request,
  onViewDetails,
  onApprove,
  onReject,
}: {
  request: RequestResponseDto;
  isLogistics: boolean;
  onViewDetails: () => void;
  onApprove: () => void;
  onReject: () => void;
}) {
  const amount = request.extraFields?.amount as number | undefined;

  return (
    <div className="border border-gray-200 rounded-xl p-4">
      <div className="flex justify-between items-start mb-3">
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          className="text-gray-700"
        >
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <path d="M14 2v6h6" />
        </svg>
        <button
          onClick={onViewDetails}
          className="text-xs text-gray-400 underline hover:text-gray-600"
        >
          Details
        </button>
      </div>

      <div className="grid grid-cols-2 gap-y-2 text-sm mb-4">
        <div>
          <p className="text-xs text-gray-400">Name</p>
          <p className="font-medium text-gray-900">{request.initiatorName}</p>
        </div>
        <div>
          <p className="text-xs text-gray-400">Date</p>
          <p className="font-medium text-gray-900">
            {formatDate(request.createdAt)}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-400">
            {amount ? 'Price' : 'Reference'}
          </p>
          <p className="font-medium text-gray-900">
            {amount ? `${amount.toLocaleString()} Rwf` : request.referenceNumber}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-400">Type</p>
          <p className="font-medium text-gray-900">
            {request.type.charAt(0) + request.type.slice(1).toLowerCase()}
          </p>
        </div>
      </div>

      <div className="flex gap-2">
        <button
          onClick={onApprove}
          className="flex-1 bg-green-100 text-green-700 py-2 rounded-lg text-sm font-medium hover:bg-green-200"
        >
          Accept
        </button>
        <button
          onClick={onReject}
          className="flex-1 bg-red-100 text-red-600 py-2 rounded-lg text-sm font-medium hover:bg-red-200"
        >
          Reject
        </button>
      </div>
    </div>
  );
}