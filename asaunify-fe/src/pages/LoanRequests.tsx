import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { requestsApi } from "../api/requests.api";
import { useAuthStore } from "../store/authStore";
import { usePermissions } from "../hooks/usePermissions";
import SearchFilterBar from "../components/ui/SearchFilterBar";
import StatusBadge from "../components/ui/StatusBadge";
import ApprovalActionModal from "../components/requests/ApprovalActionModal";
import {
  RequestStatus,
  StageStatus,
  type StageActionType,
} from "../types/enums";
import { formatRelativeDay } from "../utils/formatDate";
import { toastSuccess, toastApiError } from "../utils/toast";
import type { RequestResponseDto } from "../types/request.types";

export default function LoanApprovals() {
  const navigate = useNavigate();
  const role = useAuthStore((state) => state.role);
  const { isAdmin } = usePermissions();

  const [loans, setLoans] = useState<RequestResponseDto[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [activeRequest, setActiveRequest] = useState<RequestResponseDto | null>(
    null,
  );
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadLoans = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await requestsApi.getLoanRequests();
      setLoans(data);
    } catch (error) {
      console.error("Failed to load loan requests:", error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadLoans();
  }, [loadLoans]);

  async function handleConfirm(comment: string) {
    if (!activeRequest || !modalAction) return;
    setIsSubmitting(true);
    try {
      await requestsApi.processAction(activeRequest.id, {
        action: modalAction,
        comment: comment || undefined,
      });
      setModalAction(null);
      setActiveRequest(null);
      toastSuccess(
        modalAction === StageStatus.APPROVED
          ? "Loan request approved"
          : "Loan request rejected",
      );
      loadLoans();
    } catch (error) {
      toastApiError(error, "Failed to process action");
    } finally {
      setIsSubmitting(false);
    }
  }

  function canActOnRequest(request: RequestResponseDto): boolean {
    if (isAdmin) return false;
    const activeStage = request.approvalStages.find(
      (s) => s.status === "PENDING",
    );
    return activeStage?.assignedRole === role;
  }

  // Split into pending and history
  const pending = loans.filter((r) => r.status === RequestStatus.PENDING);
  const history = loans.filter(
    (r) =>
      r.status === RequestStatus.APPROVED ||
      r.status === RequestStatus.REJECTED ||
      r.status === RequestStatus.COMPLETED,
  );

  // Apply search filter
  function filterLoans(list: RequestResponseDto[]) {
    return list.filter(
      (r) =>
        r.title.toLowerCase().includes(search.toLowerCase()) ||
        r.caseId.toLowerCase().includes(search.toLowerCase()) ||
        r.initiatorName.toLowerCase().includes(search.toLowerCase()),
    );
  }

  const filteredPending = filterLoans(pending);
  const filteredHistory = filterLoans(history);

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Loan Approvals</h1>
        <p className="text-sm text-gray-500 mt-1">
          MSME Silver and Gold loan requests
        </p>
      </div>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search by applicant, reference..."
      />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <>
          {/* Pending loans */}
          <Section
            title={`Pending (${filteredPending.length})`}
            subtitle="Loan requests awaiting review"
          >
            {filteredPending.length === 0 ? (
              <EmptyState message="No pending loan requests" />
            ) : (
              <CardGrid>
                {filteredPending.map((r) => (
                  <LoanCard
                    key={r.id}
                    request={r}
                    canAct={canActOnRequest(r)}
                    onViewDetails={() => navigate(`/requests/${r.id}`)}
                    onApprove={() => {
                      setActiveRequest(r);
                      setModalAction(StageStatus.APPROVED);
                    }}
                    onReject={() => {
                      setActiveRequest(r);
                      setModalAction(StageStatus.REJECTED);
                    }}
                  />
                ))}
              </CardGrid>
            )}
          </Section>

          {/* Loan history */}
          <Section
            title={`History (${filteredHistory.length})`}
            subtitle="Processed loan requests"
          >
            {filteredHistory.length === 0 ? (
              <EmptyState message="No loan history yet" />
            ) : (
              <CardGrid>
                {filteredHistory.map((r) => (
                  <LoanCard
                    key={r.id}
                    request={r}
                    canAct={false}
                    onViewDetails={() => navigate(`/requests/${r.id}`)}
                  />
                ))}
              </CardGrid>
            )}
          </Section>
        </>
      )}

      <ApprovalActionModal
        isOpen={modalAction !== null}
        action={modalAction}
        isLogistics={false}
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

// ─── Loan Card ────────────────────────────────────────────

function LoanCard({
  request,
  canAct,
  onViewDetails,
  onApprove,
  onReject,
}: {
  request: RequestResponseDto;
  canAct: boolean;
  onViewDetails: () => void;
  onApprove?: () => void;
  onReject?: () => void;
}) {
  const amount = request.extraFields?.amount
    ? Number(request.extraFields.amount).toLocaleString()
    : '—';

  const isTopUp = request.extraFields?.is_top_up === true;

//   const activeStage = request.approvalStages.find(
//     (s) => s.status === 'PENDING'
//   );

  return (
    <div className="border border-gray-200 rounded-xl p-4">
      {/* Header */}
      <div className="flex justify-between items-start mb-3">
        <div className="flex items-center gap-1">
          <DocIcon />
          <p className="text-sm font-medium text-gray-500">
            {request.caseId}
          </p>
        </div>
        <button
          onClick={onViewDetails}
          className="text-xs text-gray-400 underline hover:text-gray-600"
        >
          Details
        </button>
      </div>



      {/* Title + applicant */}
      <p className="font-semibold text-gray-900 mb-3">{request.title}</p>



      {/* Loan details — label above value like first card */}
      <div className="grid grid-cols-2 gap-y-3 mb-3">
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Amount</p>
          <p className="text-sm font-semibold text-gray-900">{amount} RWF</p>
        </div>
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Type</p>
          <p className="text-sm font-semibold text-gray-900">
            {isTopUp ? 'Top-up' : 'New Loan'}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Date</p>
          <p className="text-sm font-semibold text-gray-900">
            {formatRelativeDay(request.createdAt)}
          </p>
        </div>
        {/* {activeStage && (
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Stage</p>
            <p className="text-sm font-semibold text-gray-900">
              {formatRole(activeStage.assignedRole)}
            </p>
          </div>
        )} */}
        {request.status !== RequestStatus.PENDING && (
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Status</p>
            <StatusBadge status={request.status} />
          </div>
        )}
      </div>

      {/* Action buttons — only when canAct, no View Details button */}
      {canAct && onApprove && onReject && (
        <div className="flex gap-2 mt-2">
          <button
            onClick={onApprove}
            className="flex-1 bg-green-100 text-green-700 py-2 rounded-lg text-sm font-medium hover:bg-green-200"
          >
            Approve
          </button>
          <button
            onClick={onReject}
            className="flex-1 bg-red-100 text-red-600 py-2 rounded-lg text-sm font-medium hover:bg-red-200"
          >
            Reject
          </button>
        </div>
      )}

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
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
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
      width="18"
      height="18"
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

// function formatRole(role: string): string {
//   return role
//     .toLowerCase()
//     .split("_")
//     .map((w) => w[0].toUpperCase() + w.slice(1))
//     .join(" ");
// }
