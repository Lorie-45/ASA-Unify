import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { usePermissions } from "../hooks/usePermissions";
import { requestsApi } from "../api/requests.api";
// import { useAuthStore } from "../store/authStore";
import StatCard from "../components/ui/StatCard";
import SearchFilterBar from "../components/ui/SearchFilterBar";
import DataTable, { type Column } from "../components/ui/DataTable";
import { formatDate } from "../utils/formatDate";
import { RequestStatus, Role } from "../types/enums";
import type {
  RequestResponseDto,
  VehicleTripAssignmentDto,
} from "../types/request.types";

export default function Dashboard() {
  const navigate = useNavigate();
  const { isAdmin, isAuditor, isDepartmentHead, canApprove, role } =
    usePermissions();
  const isDriver = role === Role.DRIVER;
  const [allRequests, setAllRequests] = useState<RequestResponseDto[]>([]);
  const [myRequests, setMyRequests] = useState<RequestResponseDto[]>([]);
  const [pendingApprovals, setPendingApprovals] = useState<
    RequestResponseDto[]
  >([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [myTrips, setMyTrips] = useState<VehicleTripAssignmentDto[]>([]);

  useEffect(() => {
    loadDashboardData();
  }, []);

  async function loadDashboardData() {
    setIsLoading(true);
    try {
      if (isDriver) {
        // Driver only loads trips
        const trips = await requestsApi.getMyTrips();
        setMyTrips(trips);
      } else {
        const my =
          isAdmin || isAuditor
            ? await requestsApi.getAllRequests()
            : await requestsApi.getMyRequests();
        setMyRequests(my);

        if (isAdmin || isAuditor) {
          const all = await requestsApi.getAllRequests();
          setAllRequests(all);
        }

        if (canApprove) {
          const pending = await requestsApi.getPendingForRole();
          setPendingApprovals(pending);
        }
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
  const totalPending = allRequests.filter(
    (r) => r.status === RequestStatus.PENDING,
  ).length;
  const totalCompleted = allRequests.filter(
    (r) => r.status === RequestStatus.COMPLETED,
  ).length;
  const totalRejected = allRequests.filter(
    (r) => r.status === RequestStatus.REJECTED,
  ).length;

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
      {isDriver ? (
        <DriverDashboard trips={myTrips} isLoading={isLoading} />
      ) : (
        <>
          <div>
            <h1 className="text-xl font-bold text-teal mb-1">
              General Overview
            </h1>
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

          {/* System-wide stats — Admin and Auditor only */}
          {(isAdmin || isAuditor) && (
            <div>
              <h2 className="text-lg font-bold text-teal mb-1">
                System Overview
              </h2>
              <p className="text-sm text-gray-500 mb-4">
                All requests across all departments
              </p>
              <div className="flex flex-wrap gap-4">
                <StatCard
                  title="Total Requests"
                  value={allRequests.length}
                  subtitle="All time"
                  variant="filled"
                  onClick={() => navigate("/requests")}
                />
                <StatCard
                  title="Pending"
                  value={totalPending}
                  subtitle="Awaiting approval"
                  onClick={() => navigate("/requests")}
                />
                <StatCard
                  title="Completed"
                  value={totalCompleted}
                  subtitle="Fully processed"
                  onClick={() => navigate("/requests")}
                />
                <StatCard
                  title="Rejected"
                  value={totalRejected}
                  subtitle="Declined requests"
                  onClick={() => navigate("/requests")}
                />
              </div>
            </div>
          )}

          {/* Active requests table */}
          <div>
            <h2 className="text-lg font-bold text-teal mb-1">
              Active Requests
            </h2>
            <p className="text-sm text-gray-500 mb-4">
              Main table for request review
            </p>

            <div className="mb-4">
              <SearchFilterBar value={search} onChange={setSearch} />
            </div>

            {isLoading ? (
              <p className="text-sm text-gray-400 py-8 text-center">
                Loading...
              </p>
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
        </>
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

function DriverDashboard({
  trips,
  isLoading,
}: {
  trips: VehicleTripAssignmentDto[];
  isLoading: boolean;
}) {
  const navigate = useNavigate();

  const newTrips = trips.filter((t) => t.seenAt === null);
  const completedTrips = trips.filter((t) => t.requestStatus === "COMPLETED");

  // Sort upcoming trips by trip_date
  const upcomingTrips = [...trips]
    .filter((t) => t.seenAt === null)
    .sort((a, b) => {
      if (!a.tripDate) return 1;
      if (!b.tripDate) return -1;
      return new Date(a.tripDate).getTime() - new Date(b.tripDate).getTime();
    });

  if (isLoading) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-xl font-bold text-teal mb-1">My Dashboard</h1>
        <p className="text-sm text-gray-500">Overview of your assigned trips</p>
      </div>

      {/* Stats */}
      <div className="flex flex-wrap gap-4">
        <StatCard
          title="Total Trips"
          value={trips.length}
          subtitle="All assigned trips"
          variant="filled"
          onClick={() => navigate("/trips")}
        />
        <StatCard
          title="New Assignments"
          value={newTrips.length}
          subtitle="Trips to acknowledge"
          onClick={() => navigate("/trips")}
        />
        <StatCard
          title="Completed"
          value={completedTrips.length}
          subtitle="Finished trips"
          onClick={() => navigate("/trips")}
        />
      </div>

      {/* Upcoming trips */}
      <div>
        <h2 className="text-lg font-bold text-teal mb-1">Upcoming Trips</h2>
        <p className="text-sm text-gray-500 mb-4">Your next assigned trips</p>

        {upcomingTrips.length === 0 ? (
          <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-gray-400 text-sm">
            No upcoming trips assigned
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {upcomingTrips.slice(0, 3).map((trip) => (
              <div
                key={trip.id}
                onClick={() => navigate("/trips")}
                className="border border-primary/30 bg-primary/5 rounded-xl p-4 cursor-pointer hover:shadow-md transition-shadow"
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-semibold text-primary bg-primary/10 px-2 py-1 rounded-full">
                    New
                  </span>
                  <span className="text-xs text-gray-400">
                    {trip.requestReferenceNumber}
                  </span>
                </div>

                <p className="font-semibold text-gray-900 text-sm mb-3">
                  {trip.requestTitle}
                </p>

                <div className="space-y-1.5 text-sm text-gray-600">
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400 text-xs w-16">
                      Destination
                    </span>
                    <span className="font-medium">
                      {trip.destination || "—"}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400 text-xs w-16">Date</span>
                    <span className="font-medium">{trip.tripDate || "—"}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-400 text-xs w-16">Purpose</span>
                    <span className="font-medium text-xs">
                      {trip.purpose || "—"}
                    </span>
                  </div>
                </div>

                {trip.note && (
                  <div className="mt-3 bg-white rounded-lg px-3 py-2">
                    <p className="text-xs text-gray-500">Note: {trip.note}</p>
                  </div>
                )}

                <p className="text-xs text-gray-400 mt-3">
                  Assigned by {trip.assignedByName}
                </p>
              </div>
            ))}
          </div>
        )}

        {upcomingTrips.length > 3 && (
          <button
            onClick={() => navigate("/trips")}
            className="mt-4 text-sm text-primary font-medium underline hover:text-primary-dark"
          >
            View all {upcomingTrips.length} trips →
          </button>
        )}
      </div>

      {/* All trips table */}
      {trips.length > 0 && (
        <div>
          <h2 className="text-lg font-bold text-teal mb-1">All Trips</h2>
          <p className="text-sm text-gray-500 mb-4">
            Complete history of your assigned trips
          </p>
          <div className="border border-gray-200 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="text-left px-5 py-3 font-medium text-gray-500">
                    Reference
                  </th>
                  <th className="text-left px-5 py-3 font-medium text-gray-500">
                    Destination
                  </th>
                  <th className="text-left px-5 py-3 font-medium text-gray-500">
                    Trip Date
                  </th>
                  <th className="text-left px-5 py-3 font-medium text-gray-500">
                    Status
                  </th>
                  <th className="text-left px-5 py-3 font-medium text-gray-500">
                    Acknowledged
                  </th>
                </tr>
              </thead>
              <tbody>
                {trips.map((trip) => (
                  <tr
                    key={trip.id}
                    onClick={() => navigate("/trips")}
                    className="border-t border-gray-100 hover:bg-gray-50 cursor-pointer"
                  >
                    <td className="px-5 py-4 font-medium text-gray-900">
                      {trip.requestReferenceNumber}
                    </td>
                    <td className="px-5 py-4 text-gray-600">
                      {trip.destination || "—"}
                    </td>
                    <td className="px-5 py-4 text-gray-600">
                      {trip.tripDate || "—"}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`text-sm font-semibold ${
                          trip.requestStatus === "COMPLETED"
                            ? "text-status-approved"
                            : "text-status-pending"
                        }`}
                      >
                        {trip.requestStatus === "COMPLETED"
                          ? "Completed"
                          : "Active"}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-gray-600">
                      {trip.seenAt ? (
                        <span className="text-status-approved font-medium text-xs">
                          ✓ Yes
                        </span>
                      ) : (
                        <span className="text-status-rejected font-medium text-xs">
                          Not yet
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
