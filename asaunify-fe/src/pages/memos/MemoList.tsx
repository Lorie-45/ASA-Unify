import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, ClipboardList } from "lucide-react";
import { memosApi } from "../../api/memos.api";
import { usePermissions } from "../../hooks/usePermissions";
import { useAuthStore } from "../../store/authStore";
import Button from "../../components/ui/Button";
import { formatDate } from "../../utils/formatDate";
import { RequestStatus } from "../../types/enums";
import type { MemoDto } from "../../types/memo.types";

export default function MemoList() {
  const navigate = useNavigate();
  const { canInitiateRequests, isAdmin, isAuditor } = usePermissions();
  const userId = useAuthStore((state) => state.userId);

  const [myMemos, setMyMemos] = useState<MemoDto[]>([]);
  const [pendingApproval, setPendingApproval] = useState<MemoDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const myCreatedMemos = myMemos.filter((m) => m.authorId === userId);

  const loadMemos = useCallback(async () => {
    setIsLoading(true);
    try {
      const [myResult, pendingResult] = await Promise.allSettled([
        isAdmin || isAuditor ? memosApi.getAllMemos() : memosApi.getMyMemos(),
        memosApi.getPendingForRole(),
      ]);

      if (myResult.status === "fulfilled") setMyMemos(myResult.value);
      if (pendingResult.status === "fulfilled")
        setPendingApproval(pendingResult.value);
    } catch (error) {
      console.error("Failed to load memos:", error);
    } finally {
      setIsLoading(false);
    }
  }, [isAdmin, isAuditor]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadMemos();
  }, [loadMemos]);

  // My memos — split into current and past
  const myCurrentMemos = myMemos.filter(
    (m) => m.status === RequestStatus.PENDING,
  );
  const myPastMemos = myMemos.filter(
    (m) =>
      m.status === RequestStatus.APPROVED ||
      m.status === RequestStatus.REJECTED,
  );

  // Pending approval — memos sent to me that I haven't acted on yet
  // Exclude memos I created myself
  const pendingForMe = pendingApproval.filter((m) => m.authorId !== userId);

  return (
    <div className="space-y-10">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Memos</h1>
        {canInitiateRequests && (
          <Button
            icon={<Plus size={18} />}
            onClick={() => navigate("/memos/new")}
          >
            New Memo
          </Button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <>
          {/* Memos pending my approval */}
          {pendingForMe.length > 0 && (
            <Section
              title="Pending My Approval"
              subtitle="Memos sent to you that require your decision"
            >
              <CardGrid>
                {pendingForMe.map((memo) => (
                  <MemoCard
                    key={memo.id}
                    memo={memo}
                    onClick={() => navigate(`/memos/${memo.id}`)}
                    badge="Needs Action"
                    badgeColor="bg-primary/10 text-primary"
                  />
                ))}
              </CardGrid>
            </Section>
          )}

          {/* might be a duplicate */}
          <Section title="My Memos" subtitle="Memos you have created">
            {myCreatedMemos.length === 0 ? (
              <EmptyState message="You haven't created any memos yet" />
            ) : (
              <CardGrid>
                {myCreatedMemos.map((memo) => (
                  <MemoCard
                    key={memo.id}
                    memo={memo}
                    onClick={() => navigate(`/memos/${memo.id}`)}
                    badge={
                      memo.status === RequestStatus.PENDING
                        ? "Pending Approval"
                        : undefined
                    }
                    badgeColor="bg-gray-100 text-gray-500"
                  />
                ))}
              </CardGrid>
            )}
          </Section>

          {/* My current memos */}
          <Section
            title={isAdmin || isAuditor ? "Pending Memos" : "My Memos"}
            subtitle={
              isAdmin || isAuditor
                ? "Memos across the system still in progress"
                : "Memos you have created that are still in progress"
            }
          >
            {myCurrentMemos.length === 0 ? (
              <EmptyState message="No active memos" />
            ) : (
              <CardGrid>
                {myCurrentMemos.map((memo) => (
                  <MemoCard
                    key={memo.id}
                    memo={memo}
                    onClick={() => navigate(`/memos/${memo.id}`)}
                    badge="Pending Approval"
                    badgeColor="bg-gray-100 text-gray-500"
                  />
                ))}
              </CardGrid>
            )}
          </Section>

          {/* Past memos */}
          <Section
            title="Past Memos"
            subtitle="Memos that have been fully processed"
          >
            {myPastMemos.length === 0 ? (
              <EmptyState message="No completed memos yet" />
            ) : (
              <CardGrid>
                {myPastMemos.map((memo) => (
                  <MemoCard
                    key={memo.id}
                    memo={memo}
                    onClick={() => navigate(`/memos/${memo.id}`)}
                  />
                ))}
              </CardGrid>
            )}
          </Section>
        </>
      )}
    </div>
  );
}

// ─── Memo Card ────────────────────────────────────────────

function MemoCard({
  memo,
  onClick,
  badge,
  badgeColor,
}: {
  memo: MemoDto;
  onClick: () => void;
  badge?: string;
  badgeColor?: string;
}) {
  const statusColor =
    memo.status === RequestStatus.PENDING
      ? "text-status-pending"
      : memo.status === RequestStatus.APPROVED
        ? "text-status-approved"
        : "text-status-rejected";

  const statusLabel =
    memo.status === RequestStatus.PENDING
      ? "Pending"
      : memo.status === RequestStatus.APPROVED
        ? "Approved"
        : "Rejected";

  return (
    <div
      className="border border-gray-200 rounded-xl p-4 cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
    >
      <div className="flex justify-between items-start mb-3">
        <ClipboardList size={20} className="text-gray-700" />
        <button
          onClick={(e) => {
            e.stopPropagation();
            onClick();
          }}
          className="text-xs text-gray-400 underline hover:text-gray-600"
        >
          Details
        </button>
      </div>

      <p className="text-xs text-gray-400 mb-1">{memo.referenceNumber}</p>
      <p className="font-semibold text-gray-900 mb-1">{memo.title}</p>
      <p className="text-xs text-gray-500 mb-3">By {memo.authorName}</p>

      <div className="flex items-center justify-between">
        <span className={`text-sm font-medium ${statusColor}`}>
          {statusLabel}
        </span>
        {badge && (
          <span
            className={`text-xs font-medium px-2 py-1 rounded-full ${badgeColor}`}
          >
            {badge}
          </span>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-2">{formatDate(memo.updatedAt)}</p>
    </div>
  );
}

// ─── Building blocks ──────────────────────────────────────

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
