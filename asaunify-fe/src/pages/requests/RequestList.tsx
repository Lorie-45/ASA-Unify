import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { requestsApi } from '../../api/requests.api';
import { usePermissions } from '../../hooks/usePermissions';
import StatusBadge from '../../components/ui/StatusBadge';
import { formatRelativeDay } from '../../utils/formatDate';
import { RequestStatus } from '../../types/enums';
import type { RequestResponseDto } from '../../types/request.types';
import Button from '../../components/ui/Button';

export default function RequestList() {
  const navigate = useNavigate();

  const [requests, setRequests] = useState<RequestResponseDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { canInitiateRequests, isAdmin, isAuditor } = usePermissions();

  // const loadRequests = useCallback(async () => {
  //   setIsLoading(true);
  //   try {
  //     const data = await requestsApi.getMyRequests();
  //     setRequests(data);
  //   } catch (error) {
  //     console.error('Failed to load requests:', error);
  //   } finally {
  //     setIsLoading(false);
  //   }
  // }, []);

  const loadRequests = useCallback(async () => {
  setIsLoading(true);
  try {
    // Admin and Auditor see all requests
    const data = isAdmin || isAuditor
      ? await requestsApi.getAllRequests()
      : await requestsApi.getMyRequests();
    setRequests(data);
  } catch (error) {
    console.error('Failed to load requests:', error);
  } finally {
    setIsLoading(false);
  }
}, [isAdmin, isAuditor]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadRequests();
  }, [loadRequests]);

  async function handleSubmitDraft(id: string) {
    try {
      await requestsApi.submitRequest(id);
      loadRequests();
    } catch (error) {
      console.error('Failed to submit request:', error);
    }
  }


  const drafts = requests.filter((r) => r.status === RequestStatus.DRAFT);
  const pending = requests.filter((r) => r.status === RequestStatus.PENDING);
  const past = requests.filter(
    (r) =>
      r.status === RequestStatus.APPROVED ||
      r.status === RequestStatus.REJECTED ||
      r.status === RequestStatus.COMPLETED
  );

  if (isLoading) {
    return <p className="text-sm text-gray-400 text-center py-12">Loading...</p>;
  }

  return (
    <div className="space-y-10">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Create Requests</h1>
        {canInitiateRequests && (
          <Button
            icon={<Plus size={18} />}
            onClick={() => navigate('/requests/new')}
          >
            New Purchase Order
          </Button>
        )}
      </div>

      {/* Drafts */}
      <Section title="Drafts" subtitle="Saved requests in progress">
        {drafts.length === 0 ? (
          <EmptyState message="No drafts yet" />
        ) : (
          <CardGrid>
            {drafts.map((r) => (
              <div
                key={r.id}
                className="border border-gray-200 rounded-xl p-4"
              >
                <div className="flex justify-between items-start mb-3">
                  <DocIcon />
                  <button
                    onClick={() => navigate(`/requests/${r.id}`)}
                    className="text-xs text-gray-400 underline"
                  >
                    Details
                  </button>
                </div>
                <p className="text-xs text-gray-400 mb-1">
                  {r.referenceNumber}
                </p>
                <p className="font-semibold text-gray-900 mb-3">{r.title}</p>
                <p className="text-sm text-gray-500 mb-3">
                  {formatRelativeDay(r.createdAt)}
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => navigate(`/requests/${r.id}`)}
                    className="flex-1 bg-blue-100 text-blue-700 py-2 rounded-lg text-sm font-medium hover:bg-blue-200"
                  >
                    Edit Draft
                  </button>
                  <button
                    onClick={() => handleSubmitDraft(r.id)}
                    className="flex-1 bg-primary text-white py-2 rounded-lg text-sm font-medium hover:bg-primary-dark"
                  >
                    Send Draft
                  </button>
                </div>
              </div>
            ))}
          </CardGrid>
        )}
      </Section>

      {/* Pending */}
      <Section title="Pending Requests" subtitle="Requests sent to the department head">
        {pending.length === 0 ? (
          <EmptyState message="No pending requests" />
        ) : (
          <CardGrid>
            {pending.map((r) => (
              <div
                key={r.id}
                className="border border-gray-200 rounded-xl p-4"
              >
                <div className="flex justify-between items-start mb-3">
                  <DocIcon />
                  <button
                    onClick={() => navigate(`/requests/${r.id}`)}
                    className="text-xs text-gray-400 underline"
                  >
                    Details
                  </button>
                </div>
                <p className="font-semibold text-gray-900 mb-3">{r.title}</p>
                <p className="text-xs text-gray-400 mb-1">Status</p>
                <StatusBadge status={r.status} />
              </div>
            ))}
          </CardGrid>
        )}
      </Section>

      {/* Past */}
      <Section title="Past Requests" subtitle="Requests that have already been processed">
        {past.length === 0 ? (
          <EmptyState message="No past requests" />
        ) : (
          <CardGrid>
            {past.map((r) => (
              <div
                key={r.id}
                className="border border-gray-200 rounded-xl p-4"
              >
                <div className="flex justify-between items-start mb-3">
                  <DocIcon />
                  <button
                    onClick={() => navigate(`/requests/${r.id}`)}
                    className="text-xs text-gray-400 underline"
                  >
                    Details
                  </button>
                </div>
                <p className="font-semibold text-gray-900 mb-3">{r.title}</p>
                <p className="text-xs text-gray-400 mb-1">Status</p>
                <StatusBadge status={r.status} />
                {r.status === RequestStatus.REJECTED && r.rejectionReason && (
                  <button
                    onClick={() => navigate(`/requests/${r.id}`)}
                    className="text-xs text-primary underline mt-1 block"
                  >
                    Comment
                  </button>
                )}
              </div>
            ))}
          </CardGrid>
        )}
      </Section>
    </div>
  );
}

// ─── Small local building blocks ─────────────────────────

function Section({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h2 className="text-lg font-bold text-teal mb-1">{title}</h2>
      <p className="text-sm text-gray-500 mb-4">{subtitle}</p>
      {children}
    </div>
  );
}

function CardGrid({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {children}
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-gray-400 text-sm">
      {message}
    </div>
  );
}

function DocIcon() {
  return (
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
  );
}