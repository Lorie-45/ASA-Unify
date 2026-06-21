import { useCallback, useEffect, useState } from 'react';
import { useParams,  Link } from 'react-router-dom';
import { Send, CheckCircle } from 'lucide-react';
import { requestsApi } from '../../api/requests.api';
import { usePermissions } from '../../hooks/usePermissions';
import { useAuthStore } from '../../store/authStore';
import { formatDate } from '../../utils/formatDate';
import { Role, StageStatus, type StageActionType } from '../../types/enums';
import type { RequestResponseDto } from '../../types/request.types';
import Button from '../../components/ui/Button';
import StatusBadge from '../../components/ui/StatusBadge';
import StageTracker from '../../components/requests/StageTracker';
import ApprovalActionModal from '../../components/requests/ApprovalActionModal';

export default function RequestDetail() {
  const { id } = useParams<{ id: string }>();
//   const navigate = useNavigate();
  const { canCancelRequests } = usePermissions();
  const role = useAuthStore((state) => state.role);

  const [request, setRequest] = useState<RequestResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadRequest = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    try {
      const data = await requestsApi.getRequestById(id);
      setRequest(data);
    } catch (error) {
      console.error('Failed to load request:', error);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadRequest();
  }, [loadRequest]);

  // Can the current user act on this request right now?
  const activeStage = request?.approvalStages.find(
    (s) => s.status === StageStatus.PENDING
  );
  const isCurrentApprover =
    activeStage && role && activeStage.assignedRole === role;
  const isLogisticsStage = role === Role.LOGISTICS;

  async function handleConfirmAction(comment: string, inStock?: boolean) {
    if (!request || !modalAction) return;
    setIsSubmitting(true);
    try {
      await requestsApi.processAction(request.id, {
        action: modalAction,
        comment: comment || undefined,
        inStock,
      });
      setModalAction(null);
      loadRequest();
    } catch (error) {
      console.error('Failed to process action:', error);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleCancel() {
    if (!request) return;
    const reason = window.prompt('Reason for cancelling this request:');
    if (!reason) return;
    try {
      await requestsApi.cancelRequest(request.id, reason);
      loadRequest();
    } catch (error) {
      console.error('Failed to cancel request:', error);
    }
  }

  if (isLoading) {
    return <p className="text-sm text-gray-400 text-center py-12">Loading...</p>;
  }

  if (!request) {
    return <p className="text-sm text-gray-400 text-center py-12">Request not found.</p>;
  }

  return (
    <div className="max-w-4xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <Link
            to="/requests"
            className="text-xs text-gray-400 hover:text-gray-600"
          >
            Requests &raquo; {request.referenceNumber}
          </Link>
          <h1 className="text-2xl font-bold text-teal">
            {request.referenceNumber}
          </h1>
        </div>

        <div className="flex gap-3">
          {isCurrentApprover && (
            <>
              <Button
                variant="outline"
                icon={<Send size={16} />}
                onClick={() => setModalAction(StageStatus.REJECTED)}
              >
                Reject
              </Button>
              <Button
                icon={<CheckCircle size={16} />}
                onClick={() => setModalAction(StageStatus.APPROVED)}
              >
                {isLogisticsStage ? 'Process Request' : 'Approve Request'}
              </Button>
            </>
          )}
          {canCancelRequests &&
            request.status !== 'COMPLETED' &&
            request.status !== 'REJECTED' && (
              <Button variant="danger" onClick={handleCancel}>
                Cancel Request
              </Button>
            )}
        </div>
      </div>

      {/* Description */}
      <section className="mb-8">
        <h2 className="text-lg font-bold text-teal mb-4">
          Request Description
        </h2>
        <div className="grid grid-cols-4 gap-6">
          <Field label="Request Title" value={request.title} />
          <Field label="Department" value={request.departmentName} />
          <Field label="Status" value={<StatusBadge status={request.status} />} />
          <Field label="Initiator" value={request.initiatorName} />
        </div>
        {request.dueDate && (
          <div className="mt-4">
            <Field label="Due Date" value={formatDate(request.dueDate)} />
          </div>
        )}
        {request.parentReferenceNumber && (
          <div className="mt-4">
            <Field
              label="Top-up of"
              value={
                <Link
                  to={`/requests/${request.parentReferenceNumber}`}
                  className="text-primary underline"
                >
                  {request.parentReferenceNumber}
                </Link>
              }
            />
          </div>
        )}
      </section>

      {/* Rejection banner */}
      {request.status === 'REJECTED' && request.rejectionReason && (
        <section className="mb-8 bg-red-50 border border-red-200 rounded-xl p-4">
          <p className="text-sm font-semibold text-status-rejected mb-1">
            Rejected by {request.rejectedByName}
          </p>
          <p className="text-sm text-gray-700">{request.rejectionReason}</p>
        </section>
      )}

      {/* Request details — type-specific extra fields */}
      <section className="mb-8">
        <h2 className="text-lg font-bold text-teal mb-4">Request Details</h2>
        <div className="border border-gray-200 rounded-xl p-5">
          <p className="text-sm text-gray-700 mb-4">{request.details}</p>
          {request.extraFields && (
            <div className="grid grid-cols-3 gap-4 pt-4 border-t border-gray-100">
              {Object.entries(request.extraFields).map(([key, value]) => (
                <Field
                  key={key}
                  label={formatFieldLabel(key)}
                  value={String(value)}
                />
              ))}
            </div>
          )}
          {request.notes && (
            <div className="pt-4 mt-4 border-t border-gray-100">
              <Field label="Notes" value={request.notes} />
            </div>
          )}
        </div>
      </section>

      {/* Attachments */}
      {request.attachments.length > 0 && (
        <section className="mb-8">
          <h2 className="text-lg font-bold text-teal mb-4">Attachments</h2>
          <div className="space-y-2">
            {request.attachments.map((a) => (
              <div
                key={a.id}
                className="flex items-center justify-between border border-gray-200 rounded-lg px-4 py-3 text-sm"
              >
                <span className="text-gray-700">{a.fileName}</span>
                <span className="text-gray-400 text-xs">
                  {a.uploadedByName} · {formatDate(a.uploadedAt)}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Approval timeline */}
      <section>
        <h2 className="text-lg font-bold text-teal mb-4">Approval Timeline</h2>
        <StageTracker stages={request.approvalStages} />
      </section>

      <ApprovalActionModal
        isOpen={modalAction !== null}
        action={modalAction}
        isLogistics={isLogisticsStage}
        onClose={() => setModalAction(null)}
        onConfirm={handleConfirmAction}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}

// ─── Helpers ──────────────────────────────────────────────

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs text-gray-400 mb-1">{label}</p>
      <p className="text-sm font-medium text-gray-900">{value}</p>
    </div>
  );
}

function formatFieldLabel(key: string): string {
  return key
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}