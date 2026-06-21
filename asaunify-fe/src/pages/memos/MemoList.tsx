import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, ClipboardList } from 'lucide-react';
import { memosApi } from '../../api/memos.api';
import { usePermissions } from '../../hooks/usePermissions';
import Button from '../../components/ui/Button';
import { formatDate } from '../../utils/formatDate';
import { RequestStatus } from '../../types/enums';
import type { MemoDto } from '../../types/memo.types';

export default function MemoList() {
  const navigate = useNavigate();
  const { canInitiateRequests } = usePermissions();

  const [memos, setMemos] = useState<MemoDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadMemos = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await memosApi.getMyMemos();
      setMemos(data);
    } catch (error) {
      console.error('Failed to load memos:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadMemos();
  }, [loadMemos]);

  const current = memos.filter((m) => m.status === RequestStatus.PENDING);
  const past = memos.filter(
    (m) =>
      m.status === RequestStatus.APPROVED || m.status === RequestStatus.REJECTED
  );

  return (
    <div className="space-y-10">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Create Memo</h1>
        {canInitiateRequests && (
          <Button
            icon={<Plus size={18} />}
            onClick={() => navigate('/memos/new')}
          >
            New Memo
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <>
          <Section title="Current Memos" subtitle="Saved memos waiting for approval">
            {current.length === 0 ? (
              <EmptyState message="No memos pending approval" />
            ) : (
              <CardGrid>
                {current.map((memo) => (
                  <MemoCard key={memo.id} memo={memo} onClick={() => navigate(`/memos/${memo.id}`)} />
                ))}
              </CardGrid>
            )}
          </Section>

          <Section title="Past Memos" subtitle="Completed memos">
            {past.length === 0 ? (
              <EmptyState message="No completed memos yet" />
            ) : (
              <CardGrid>
                {past.map((memo) => (
                  <MemoCard key={memo.id} memo={memo} onClick={() => navigate(`/memos/${memo.id}`)} />
                ))}
              </CardGrid>
            )}
          </Section>
        </>
      )}
    </div>
  );
}

// ─── Building blocks ──────────────────────────────────────

function MemoCard({ memo, onClick }: { memo: MemoDto; onClick: () => void }) {
  const statusLabel =
    memo.status === RequestStatus.PENDING
      ? 'Pending Approval'
      : memo.status === RequestStatus.APPROVED
      ? 'Completed'
      : 'Rejected';

  const statusColor =
    memo.status === RequestStatus.PENDING
      ? 'text-status-pending'
      : memo.status === RequestStatus.APPROVED
      ? 'text-status-approved'
      : 'text-status-rejected';

  return (
    <div className="border border-gray-200 rounded-xl p-4">
      <div className="flex justify-between items-start mb-3">
        <ClipboardList size={20} className="text-gray-700" />
        <button
          onClick={onClick}
          className="text-xs text-gray-400 underline hover:text-gray-600"
        >
          Details
        </button>
      </div>
      <p className="text-xs text-gray-400 mb-1">{memo.referenceNumber}</p>
      <p className="font-semibold text-gray-900 mb-3">{memo.title}</p>
      <div className="flex items-center justify-between">
        <span className={`text-sm font-medium ${statusColor}`}>
          {statusLabel}
        </span>
        <span className="text-xs text-gray-400">
          {formatDate(memo.updatedAt)}
        </span>
      </div>
    </div>
  );
}

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