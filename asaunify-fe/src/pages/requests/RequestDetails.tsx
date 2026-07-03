import { useCallback, useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { Send, CheckCircle, UserCheck, X } from "lucide-react";
import { requestsApi } from "../../api/requests.api";
import { usePermissions } from "../../hooks/usePermissions";
import { useAuthStore } from "../../store/authStore";
import { formatDate } from "../../utils/formatDate";
import {
  RequestStatus,
  RequestType,
  Role,
  StageStatus,
  type StageActionType,
} from "../../types/enums";
import type { RequestResponseDto } from "../../types/request.types";
import Button from "../../components/ui/Button";
import StatusBadge from "../../components/ui/StatusBadge";
import StageTracker from "../../components/requests/StageTracker";
import ApprovalActionModal from "../../components/requests/ApprovalActionModal";
import { usersApi } from "../../api/users.api";
import type { UserDto } from "../../types/user.types";
import { toastApiError, toastSuccess } from "../../utils/toast";

export default function RequestDetail() {
  const { id } = useParams<{ id: string }>();
  //   const navigate = useNavigate();
  const { canCancelRequests } = usePermissions();
  const role = useAuthStore((state) => state.role);

  const [request, setRequest] = useState<RequestResponseDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const userId = useAuthStore((state) => state.userId);
  const [isEditing, setIsEditing] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editDetails, setEditDetails] = useState("");
  const [editNotes, setEditNotes] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  const isDraft = request?.status === RequestStatus.DRAFT;
  const isInitiator = request?.initiatorId === userId;
  const canEdit = isDraft && isInitiator;

  const [drivers, setDrivers] = useState<UserDto[]>([]);
  const [isAssignDriverOpen, setIsAssignDriverOpen] = useState(false);
  const [selectedDriverId, setSelectedDriverId] = useState("");
  const [driverNote, setDriverNote] = useState("");
  const [isAssigning, setIsAssigning] = useState(false);

  const isFleetManager = role === Role.FLEET_MANAGER;
  const isVehicleRequest = request?.type === RequestType.VEHICLE;
  const isApproved = request?.status === RequestStatus.APPROVED;
  const canAssignDriver = isFleetManager && isVehicleRequest && isApproved;

  // Equipment edit fields
  const [editItemName, setEditItemName] = useState("");
  const [editQuantity, setEditQuantity] = useState(1);

  // Vehicle edit fields
  const [editDestination, setEditDestination] = useState("");
  const [editTripDate, setEditTripDate] = useState("");
  const [editPurpose, setEditPurpose] = useState("");

  // Loan edit fields
  const [editAmount, setEditAmount] = useState(0);
  const [editIsTopUp, setEditIsTopUp] = useState(false);

  const loadRequest = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    try {
      const data = await requestsApi.getRequestById(id);
      setRequest(data);
    } catch (error) {
      console.error("Failed to load request:", error);
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadRequest();
  }, [loadRequest]);

  async function loadDrivers() {
    try {
      console.log("Loading drivers...");
      const data = await usersApi.getUsersByRole(Role.DRIVER);
      setDrivers(data);
      console.log("Drivers loaded:", data);
    } catch (error) {
      console.error("Failed to load drivers:", error);
    }
  }

  async function handleAssignDriver() {
    if (!request || !selectedDriverId) return;
    setIsAssigning(true);
    try {
      await requestsApi.assignDriver(request.id, {
        driverId: selectedDriverId,
        note: driverNote || undefined,
      });
      setIsAssignDriverOpen(false);
      setSelectedDriverId("");
      setDriverNote("");
      toastSuccess("Driver assigned successfully");
      loadRequest();
    } catch (error) {
      toastApiError(error, "Failed to assign driver");
    } finally {
      setIsAssigning(false);
    }
  }

  // Can the current user act on this request right now?
  const activeStage = request?.approvalStages.find(
    (s) => s.status === StageStatus.PENDING,
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
      toastSuccess(
        modalAction === StageStatus.APPROVED
          ? "Request approved successfully"
          : "Request rejected",
      );
      loadRequest();
    } catch (error) {
      toastApiError(error, "Failed to process action, try again");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleStartEdit() {
    setEditTitle(request!.title);
    setEditDetails(request!.details);
    setEditNotes(request!.notes ?? "");

    const extra = request!.extraFields ?? {};

    if (request!.type === RequestType.EQUIPMENT) {
      setEditItemName(String(extra.item_name ?? ""));
      setEditQuantity(Number(extra.quantity ?? 1));
    }

    if (request!.type === RequestType.VEHICLE) {
      setEditDestination(String(extra.destination ?? ""));
      setEditTripDate(String(extra.trip_date ?? ""));
      setEditPurpose(String(extra.purpose ?? ""));
    }

    if (request!.type === RequestType.LOAN) {
      setEditAmount(Number(extra.amount ?? 0));
      setEditIsTopUp(Boolean(extra.is_top_up ?? false));
    }

    setIsEditing(true);
  }

  async function handleSaveEdit() {
    if (!request) return;

    // Build updated extraFields based on request type
    let updatedExtraFields: Record<string, unknown> = {};

    if (request.type === RequestType.EQUIPMENT) {
      updatedExtraFields = {
        item_name: editItemName,
        quantity: editQuantity,
      };
    } else if (request.type === RequestType.VEHICLE) {
      updatedExtraFields = {
        destination: editDestination,
        trip_date: editTripDate,
        purpose: editPurpose,
      };
    } else if (request.type === RequestType.LOAN) {
      // Validate loan amount before saving
      const isSilver = editAmount >= 2_000_001 && editAmount <= 5_000_000;
      const isGold = editAmount >= 5_000_001 && editAmount <= 10_000_000;
      if (!editIsTopUp && !isSilver && !isGold) {
        alert(
          "Loan amount must be 2,000,001–5,000,000 RWF (Silver) or 5,000,001–10,000,000 RWF (Gold)",
        );
        return;
      }
      if (editIsTopUp && editAmount > 1_500_000) {
        alert("Top-up amount cannot exceed 1,500,000 RWF");
        return;
      }
      updatedExtraFields = {
        amount: editAmount,
        is_top_up: editIsTopUp,
      };
    }

    setIsSaving(true);
    try {
      await requestsApi.updateDraft(request.id, {
        title: editTitle,
        details: editDetails,
        notes: editNotes,
        extraFields: updatedExtraFields,
      });
      setIsEditing(false);
      toastSuccess("Draft edited successfully");
      loadRequest();
    } catch (error) {
      toastApiError(error, "Failed to save draft");
    } finally {
      setIsSaving(false);
    }
  }

  async function handleCancel() {
    if (!request) return;
    const reason = window.prompt("Reason for cancelling this request:");
    if (!reason) return;
    try {
      await requestsApi.cancelRequest(request.id, reason);
      toastSuccess("Request Cancelled");
      loadRequest();
    } catch (error) {
      toastApiError(error, "Failed to cancel request");
    }
  }

  if (isLoading) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
    );
  }

  if (!request) {
    return (
      <p className="text-sm text-gray-400 text-center py-12">
        Request not found.
      </p>
    );
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
          {/* Edit/Save buttons for draft initiator */}
          {canEdit && !isEditing && (
            <Button variant="outline" onClick={handleStartEdit}>
              Edit Draft
            </Button>
          )}
          {canEdit && !isEditing && (
            <Button
              onClick={async () => {
                await requestsApi.submitRequest(request.id);
                loadRequest();
              }}
            >
              Submit
            </Button>
          )}
          {isEditing && (
            <>
              <Button variant="ghost" onClick={() => setIsEditing(false)}>
                Cancel
              </Button>
              <Button disabled={isSaving} onClick={handleSaveEdit}>
                {isSaving ? "Saving..." : "Save Draft"}
              </Button>
            </>
          )}

          {/* Approve/Reject for approvers */}
          {isCurrentApprover && !isEditing && (
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
                {isLogisticsStage ? "Process Request" : "Approve Request"}
              </Button>
            </>
          )}

          {canAssignDriver && (
            <Button
              icon={<UserCheck size={16} />}
              onClick={() => {
                loadDrivers();
                setIsAssignDriverOpen(true);
              }}
            >
              Assign Driver
            </Button>
          )}

          {canCancelRequests &&
            request.status !== "COMPLETED" &&
            request.status !== "REJECTED" &&
            !isEditing && (
              <Button variant="danger" onClick={handleCancel}>
                Cancel Request
              </Button>
            )}
        </div>
      </div>

      {/* Editable fields when in edit mode */}
      {isEditing ? (
        <div className="space-y-4 mb-8">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Title
            </label>
            <input
              title="Title"
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Details
            </label>
            <textarea
              title="Details"
              value={editDetails}
              onChange={(e) => setEditDetails(e.target.value)}
              rows={5}
              className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Notes
            </label>
            <textarea
              title="Notes"
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              rows={3}
              className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
            />

            {request.type === RequestType.EQUIPMENT && (
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-gray-100">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Item Name
                  </label>
                  <input
                    title="Item Name"
                    type="text"
                    value={editItemName}
                    onChange={(e) => setEditItemName(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Quantity
                  </label>
                  <input
                    title="Quantity"
                    type="number"
                    min={1}
                    value={editQuantity}
                    onChange={(e) => setEditQuantity(Number(e.target.value))}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>
            )}

            {/* Vehicle extra fields */}
            {request.type === RequestType.VEHICLE && (
              <div className="grid grid-cols-2 gap-4 pt-4 border-t border-gray-100">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Destination
                  </label>
                  <input
                    title="Destination"
                    type="text"
                    value={editDestination}
                    onChange={(e) => setEditDestination(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Trip Date
                  </label>
                  <input
                    title="Date"
                    type="date"
                    value={editTripDate}
                    onChange={(e) => setEditTripDate(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
                <div className="col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Purpose
                  </label>
                  <input
                    title="Purpose"
                    type="text"
                    value={editPurpose}
                    onChange={(e) => setEditPurpose(e.target.value)}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                </div>
              </div>
            )}

            {/* Loan extra fields */}
            {request.type === RequestType.LOAN && (
              <div className="space-y-4 pt-4 border-t border-gray-100">
                <div className="flex items-center gap-3">
                  <input
                    type="checkbox"
                    id="editIsTopUp"
                    checked={editIsTopUp}
                    onChange={(e) => setEditIsTopUp(e.target.checked)}
                    className="w-4 h-4 accent-primary"
                  />
                  <label
                    htmlFor="editIsTopUp"
                    className="text-sm font-medium text-gray-700"
                  >
                    This is a top-up on an existing MSME Silver loan
                  </label>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Loan Amount (RWF)
                  </label>
                  <input
                    title="Amount"
                    type="number"
                    min={0}
                    value={editAmount}
                    onChange={(e) => setEditAmount(Number(e.target.value))}
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  />
                  <p className="text-xs text-gray-400 mt-1">
                    {editIsTopUp
                      ? "Maximum top-up: 1,500,000 RWF"
                      : "Silver: 2,000,001–5,000,000 RWF · Gold: 5,000,001–10,000,000 RWF"}
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      ) : (
        <section className="mb-8">
          <h2 className="text-lg font-bold text-teal mb-4">
            Request Description
          </h2>
          <div className="grid grid-cols-4 gap-6">
            <Field label="Request Title" value={request.title} />
            <Field label="Department" value={request.departmentName} />
            <Field
              label="Status"
              value={<StatusBadge status={request.status} />}
            />
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
      )}

      {/* Rejection banner */}
      {request.status === "REJECTED" && request.rejectionReason && (
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

      {/* Assign Driver Modal */}
      {isAssignDriverOpen && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl w-full max-w-md p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-gray-900">Assign Driver</h3>
              <button
                title="close"
                onClick={() => setIsAssignDriverOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                <X size={20} />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Select Driver
                </label>
                <select
                  title="Driver ID"
                  value={selectedDriverId}
                  onChange={(e) => setSelectedDriverId(e.target.value)}
                  className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                  <option value="">Choose a driver...</option>
                  {drivers.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.fullName}
                    </option>
                  ))}
                </select>
                {drivers.length === 0 && (
                  <p className="text-xs text-gray-400 mt-1">
                    No drivers available. Create a user with the Driver role
                    first.
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Note (optional)
                </label>
                <textarea
                  value={driverNote}
                  onChange={(e) => setDriverNote(e.target.value)}
                  rows={3}
                  placeholder="e.g. Pick up at 7:00 AM from main office"
                  className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>
            </div>

            <div className="flex gap-3 mt-6">
              <Button
                variant="ghost"
                onClick={() => setIsAssignDriverOpen(false)}
                className="flex-1"
              >
                Cancel
              </Button>
              <Button
                onClick={handleAssignDriver}
                disabled={isAssigning || !selectedDriverId}
                className="flex-1"
              >
                {isAssigning ? "Assigning..." : "Assign Driver"}
              </Button>
            </div>
          </div>
        </div>
      )}
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
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}
