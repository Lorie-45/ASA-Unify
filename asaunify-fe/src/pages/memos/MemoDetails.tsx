import { useCallback, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { memosApi } from '../../api/memos.api';
import { useAuthStore } from '../../store/authStore';
import Button from '../../components/ui/Button';
import ApprovalActionModal from '../../components/requests/ApprovalActionModal';
import { formatDate } from '../../utils/formatDate';
import { RequestStatus, StageStatus, type StageActionType } from '../../types/enums';
import type { MemoDto } from '../../types/memo.types';

function formatRoleLabel(role: string): string {
  return role
    .toLowerCase()
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

export default function MemoDetail() {
  const { id } = useParams<{ id: string }>();
  const role = useAuthStore((state) => state.role);

  const [memo, setMemo] = useState<MemoDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadMemo = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    try {
      const data = await memosApi.getMemoById(id);
      setMemo(data);
    } catch (error) {
      console.error('Failed to load memo:', error);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadMemo();
  }, [loadMemo]);

  const myStage = memo?.approvalStages.find((s) => s.assignedRole === role);
  const canAct = myStage && myStage.status === StageStatus.PENDING;

  async function handleConfirm(comment: string) {
    if (!memo || !modalAction) return;
    setIsSubmitting(true);
    try {
      await memosApi.processAction(memo.id, {
        action: modalAction,
        comment: comment || undefined,
      });
      setModalAction(null);
      loadMemo();
    } catch (error) {
      console.error('Failed to process memo action:', error);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return <p className="text-sm text-gray-400 text-center py-12">Loading...</p>;
  }

  if (!memo) {
    return <p className="text-sm text-gray-400 text-center py-12">Memo not found.</p>;
  }

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <div>
          <Link to="/memos" className="text-xs text-gray-400 hover:text-gray-600">
            Memos &raquo; {memo.referenceNumber}
          </Link>
          <h1 className="text-2xl font-bold text-teal">{memo.title}</h1>
        </div>

        {canAct && (
          <div className="flex gap-3">
            <Button
              variant="outline"
              onClick={() => setModalAction(StageStatus.REJECTED)}
            >
              Reject
            </Button>
            <Button onClick={() => setModalAction(StageStatus.APPROVED)}>
              Approve
            </Button>
          </div>
        )}
      </div>

      <div className="flex items-center gap-6 text-sm text-gray-500 mb-6">
        <span>By {memo.authorName}</span>
        <span>{formatDate(memo.createdAt)}</span>
      </div>

      {memo.status === RequestStatus.REJECTED && memo.rejectionReason && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4">
          <p className="text-sm font-semibold text-status-rejected mb-1">
            Rejected by {memo.rejectedByName}
          </p>
          <p className="text-sm text-gray-700">{memo.rejectionReason}</p>
        </div>
      )}

      <section className="mb-8 border border-gray-200 rounded-xl p-5">
        <p className="text-sm text-gray-700 whitespace-pre-wrap">
          {memo.content}
        </p>
      </section>

      <section>
        <h2 className="text-lg font-bold text-teal mb-4">Approvers</h2>
        <div className="space-y-3">
          {memo.approvalStages.map((stage) => (
            <div
              key={stage.id}
              className="flex items-center justify-between border border-gray-200 rounded-lg px-4 py-3"
            >
              <div>
                <p className="text-sm font-medium text-gray-900">
                  {formatRoleLabel(stage.assignedRole)}
                </p>
                {stage.actedByName && (
                  <p className="text-xs text-gray-400">by {stage.actedByName}</p>
                )}
                {stage.comment && (
                  <p className="text-sm text-gray-600 mt-1">"{stage.comment}"</p>
                )}
              </div>
              <span
                className={`text-sm font-semibold ${
                  stage.status === StageStatus.APPROVED
                    ? 'text-status-approved'
                    : stage.status === StageStatus.REJECTED
                    ? 'text-status-rejected'
                    : 'text-status-pending'
                }`}
              >
                {stage.status.charAt(0) + stage.status.slice(1).toLowerCase()}
              </span>
            </div>
          ))}
        </div>
      </section>

      <ApprovalActionModal
        isOpen={modalAction !== null}
        action={modalAction}
        onClose={() => setModalAction(null)}
        onConfirm={handleConfirm}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}