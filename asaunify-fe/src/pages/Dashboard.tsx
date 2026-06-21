import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { usePermissions } from "../hooks/usePermissions";
import { requestsApi } from "../api/requests.api";
// import { useAuthStore } from "../store/authStore";
import StatCard from "../components/ui/StatCard";
import SearchFilterBar from "../components/ui/SearchFilterBar";
import DataTable, { type Column } from "../components/ui/DataTable";
import { formatDate } from "../utils/formatDate";
import { RequestStatus } from "../types/enums";
import type { RequestResponseDto } from "../types/request.types";

export default function Dashboard() {
  const navigate = useNavigate();
  const { isAdmin, isAuditor, isDepartmentHead, canApprove } = usePermissions();
//   const fullName = useAuthStore((state) => state.fullName);

  const [myRequests, setMyRequests] = useState<RequestResponseDto[]>([]);
  const [pendingApprovals, setPendingApprovals] = useState<
    RequestResponseDto[]
  >([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadDashboardData();
  }, []);

  async function loadDashboardData() {
    setIsLoading(true);
    try {
      const requests = await requestsApi.getMyRequests();
      setMyRequests(requests);

      if (canApprove) {
        const pending = await requestsApi.getPendingForRole();
        setPendingApprovals(pending);
      }
    } catch (error) {
      console.error("Failed to load dashboard data:", error);
    } finally {
      setIsLoading(false);
    }
  }

  const drafts = myRequests.filter((r) => r.status === RequestStatus.DRAFT);
  const activeRequests = myRequests.filter(
    (r) => r.status === RequestStatus.PENDING,
  );

  const filteredActive = activeRequests.filter(
    (r) =>
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.referenceNumber.toLowerCase().includes(search.toLowerCase()),
  );

  const columns: Column<RequestResponseDto>[] = [
    { header: "Case Number", accessor: (r) => r.referenceNumber },
    { header: "Type", accessor: (r) => formatType(r.type) },
    { header: "Submitted", accessor: (r) => formatDate(r.createdAt) },
    {
      header: "Current Stage",
      accessor: (r) => currentStageLabel(r),
    },
    {
      header: "Actions",
      accessor: (r) => (
        <button
          onClick={() => navigate(`/requests/${r.id}`)}
          className="text-primary font-medium underline hover:text-primary-dark"
        >
          View
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-xl font-bold text-teal mb-1">General Overview</h1>
        <p className="text-sm text-gray-500">
          {isAdmin || isAuditor
            ? "Insights on general procurement department performance"
            : "Insights on general department performance"}
        </p>
      </div>

      {/* Stat cards — adapt based on role */}
      <div className="flex flex-wrap gap-4">
        <StatCard
          title="My Requests"
          value={myRequests.length}
          subtitle="Your requests"
          variant="filled"
          onClick={() => navigate("/requests")}
        />
        <StatCard
          title="Drafts"
          value={drafts.length}
          subtitle="Saved ongoing requests"
          onClick={() => navigate("/requests")}
        />
        {canApprove && (
          <StatCard
            title="Pending Approvals"
            value={pendingApprovals.length}
            subtitle="Requests waiting on your review"
            onClick={() => navigate("/approvals")}
          />
        )}
        {isDepartmentHead && (
          <StatCard
            title="Superior Reviews"
            value={activeRequests.length}
            subtitle="Request reviewed today"
          />
        )}
      </div>

      {/* Active requests table */}
      <div>
        <h2 className="text-lg font-bold text-teal mb-1">Active Requests</h2>
        <p className="text-sm text-gray-500 mb-4">
          Main table for request review
        </p>

        <div className="mb-4">
          <SearchFilterBar value={search} onChange={setSearch} />
        </div>

        {isLoading ? (
          <p className="text-sm text-gray-400 py-8 text-center">Loading...</p>
        ) : (
          <DataTable
            columns={columns}
            data={filteredActive}
            keyExtractor={(r) => r.id}
            emptyMessage="No active requests"
          />
        )}
      </div>

      {/* Pending approvals quick list — only for approvers */}
      {canApprove && pendingApprovals.length > 0 && (
        <div>
          <h2 className="text-lg font-bold text-teal mb-1">
            Pending Approvals Overview
          </h2>
          <p className="text-sm text-gray-500 mb-4">
            Insights on approvals requiring your answer
          </p>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {pendingApprovals.slice(0, 3).map((r) => (
              <PendingApprovalCard
                key={r.id}
                request={r}
                onRefresh={loadDashboardData}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Helpers ──────────────────────────────────────────────

function formatType(type: string): string {
  return type.charAt(0) + type.slice(1).toLowerCase();
}

function currentStageLabel(request: RequestResponseDto): string {
  const activeStage = request.approvalStages.find(
    (s) => s.status === "PENDING",
  );
  if (!activeStage) return "—";
  return activeStage.assignedRole
    .toLowerCase()
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

// ─── Inline pending approval card ────────────────────────

import { requestsApi as api } from "../api/requests.api";
import { StageStatus } from "../types/enums";

function PendingApprovalCard({
  request,
  onRefresh,
}: {
  request: RequestResponseDto;
  onRefresh: () => void;
}) {
  const [isProcessing, setIsProcessing] = useState(false);

  async function handleAction(
    action: typeof StageStatus.APPROVED | typeof StageStatus.REJECTED,
  ) {
    if (action === StageStatus.REJECTED) {
      const comment = window.prompt("Please provide a reason for rejection:");
      if (!comment) return;
      setIsProcessing(true);
      await api.processAction(request.id, { action, comment });
      setIsProcessing(false);
      onRefresh();
      return;
    }

    setIsProcessing(true);
    await api.processAction(request.id, { action });
    setIsProcessing(false);
    onRefresh();
  }

  return (
    <div className="border border-gray-200 rounded-xl p-4">
      <div className="flex justify-between items-start mb-3">
        <p className="font-semibold text-gray-900">{request.initiatorName}</p>
        <a
          href={`/requests/${request.id}`}
          className="text-xs text-gray-400 underline"
        >
          Details
        </a>
      </div>
      <div className="flex justify-between text-sm text-gray-500 mb-1">
        <span>Price</span>
        <span>Date</span>
      </div>
      <div className="flex justify-between text-sm mb-4">
        <span className="font-medium text-gray-800">
          {(request.extraFields?.amount as number)?.toLocaleString() ?? "—"} Rwf
        </span>
        <span>{formatDate(request.createdAt)}</span>
      </div>
      <div className="flex gap-2">
        <button
          disabled={isProcessing}
          onClick={() => handleAction(StageStatus.APPROVED)}
          className="flex-1 bg-green-100 text-green-700 py-2 rounded-lg text-sm font-medium hover:bg-green-200 disabled:opacity-50"
        >
          Accept
        </button>
        <button
          disabled={isProcessing}
          onClick={() => handleAction(StageStatus.REJECTED)}
          className="flex-1 bg-red-100 text-red-600 py-2 rounded-lg text-sm font-medium hover:bg-red-200 disabled:opacity-50"
        >
          Reject
        </button>
      </div>
    </div>
  );
}
