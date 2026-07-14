// import { useCallback, useEffect, useState } from "react";
// import { useNavigate } from "react-router-dom";
// import { requestsApi } from "../api/requests.api";
// import { useAuthStore } from "../store/authStore";
// import SearchFilterBar from "../components/ui/SearchFilterBar";
// import ApprovalActionModal from "../components/requests/ApprovalActionModal";
// import { Role, StageStatus, type StageActionType } from "../types/enums";
// import { formatDate } from "../utils/formatDate";
// import type { RequestResponseDto } from "../types/request.types";
// import { toastApiError, toastSuccess } from "../utils/toast";

// export default function Approvals() {
//   const navigate = useNavigate();
//   const role = useAuthStore((state) => state.role);

//   const [requests, setRequests] = useState<RequestResponseDto[]>([]);
//   const [search, setSearch] = useState("");
//   const [isLoading, setIsLoading] = useState(true);
//   const [activeRequest, setActiveRequest] = useState<RequestResponseDto | null>(
//     null,
//   );
//   const [modalAction, setModalAction] = useState<StageActionType | null>(null);
//   const [isSubmitting, setIsSubmitting] = useState(false);

//   const loadPending = useCallback(async () => {
//     setIsLoading(true);
//     try {
//       const data = await requestsApi.getPendingForRole();
//       setRequests(data);
//     } catch (error) {
//       console.error("Failed to load pending approvals:", error);
//     } finally {
//       setIsLoading(false);
//     }
//   }, []);

//   useEffect(() => {
//     // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
//     loadPending();
//   }, [loadPending]);

//   function openAction(request: RequestResponseDto, action: StageActionType) {
//     setActiveRequest(request);
//     setModalAction(action);
//   }

//   async function handleConfirm(comment: string, inStock?: boolean) {
//     if (!activeRequest || !modalAction) return;
//     setIsSubmitting(true);
//     try {
//       await requestsApi.processAction(activeRequest.id, {
//         action: modalAction,
//         comment: comment || undefined,
//         inStock,
//       });
//       setModalAction(null);
//       setActiveRequest(null);
//       toastSuccess(
//         modalAction === StageStatus.APPROVED
//           ? "Request approved"
//           : "Request rejected",
//       );
//       loadPending();
//     } catch (error) {
//       toastApiError(error, "Failed to process action");
//     } finally {
//       setIsSubmitting(false);
//     }
//   }

//   const filtered = requests.filter(
//     (r) =>
//       r.title.toLowerCase().includes(search.toLowerCase()) ||
//       r.referenceNumber.toLowerCase().includes(search.toLowerCase()) ||
//       r.initiatorName.toLowerCase().includes(search.toLowerCase()),
//   );

//   return (
//     <div className="space-y-6">
//       <h1 className="text-xl font-bold text-gray-900">Approvals</h1>

//       <SearchFilterBar
//         value={search}
//         onChange={setSearch}
//         placeholder="Search by name, reference..."
//       />

//       {isLoading ? (
//         <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
//       ) : filtered.length === 0 ? (
//         <div className="border border-dashed border-gray-200 rounded-xl p-12 text-center text-gray-400 text-sm">
//           Nothing waiting on your approval right now.
//         </div>
//       ) : (
//         <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
//           {filtered.map((request) => (
//             <ApprovalCard
//               key={request.id}
//               request={request}
//               isLogistics={role === Role.LOGISTICS}
//               onViewDetails={() => navigate(`/requests/${request.id}`)}
//               onApprove={() => openAction(request, StageStatus.APPROVED)}
//               onReject={() => openAction(request, StageStatus.REJECTED)}
//             />
//           ))}
//         </div>
//       )}

//       <ApprovalActionModal
//         isOpen={modalAction !== null}
//         action={modalAction}
//         isLogistics={role === Role.LOGISTICS}
//         onClose={() => {
//           setModalAction(null);
//           setActiveRequest(null);
//         }}
//         onConfirm={handleConfirm}
//         isSubmitting={isSubmitting}
//       />
//     </div>
//   );
// }

// // ─── Card ─────────────────────────────────────────────────

// function ApprovalCard({
//   request,
//   onViewDetails,
//   onApprove,
//   onReject,
// }: {
//   request: RequestResponseDto;
//   isLogistics: boolean;
//   onViewDetails: () => void;
//   onApprove: () => void;
//   onReject: () => void;
// }) {
//   const amount = request.extraFields?.amount as number | undefined;

//   return (
//     <div className="border border-gray-200 rounded-xl p-4">
//       <div className="flex justify-between items-start mb-3">
//         <svg
//           width="20"
//           height="20"
//           viewBox="0 0 24 24"
//           fill="none"
//           stroke="currentColor"
//           strokeWidth="1.5"
//           className="text-gray-700"
//         >
//           <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
//           <path d="M14 2v6h6" />
//         </svg>
//         <button
//           onClick={onViewDetails}
//           className="text-xs text-gray-400 underline hover:text-gray-600"
//         >
//           Details
//         </button>
//       </div>

//       <div className="grid grid-cols-2 gap-y-2 text-sm mb-4">
//         <div>
//           <p className="text-xs text-gray-400">Name</p>
//           <p className="font-medium text-gray-900">{request.initiatorName}</p>
//         </div>
//         <div>
//           <p className="text-xs text-gray-400">Date</p>
//           <p className="font-medium text-gray-900">
//             {formatDate(request.createdAt)}
//           </p>
//         </div>
//         <div>
//           <p className="text-xs text-gray-400">
//             {amount ? "Price" : "Reference"}
//           </p>
//           <p className="font-medium text-gray-900">
//             {amount
//               ? `${amount.toLocaleString()} Rwf`
//               : request.referenceNumber}
//           </p>
//         </div>
//         <div>
//           <p className="text-xs text-gray-400">Type</p>
//           <p className="font-medium text-gray-900">
//             {request.type.charAt(0) + request.type.slice(1).toLowerCase()}
//           </p>
//         </div>
//       </div>

//       <div className="flex gap-2">
//         <button
//           onClick={onApprove}
//           className="flex-1 bg-green-100 text-green-700 py-2 rounded-lg text-sm font-medium hover:bg-green-200"
//         >
//           Accept
//         </button>
//         <button
//           onClick={onReject}
//           className="flex-1 bg-red-100 text-red-600 py-2 rounded-lg text-sm font-medium hover:bg-red-200"
//         >
//           Reject
//         </button>
//       </div>
//     </div>
//   );
// }



import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { requestsApi } from '../api/requests.api';
import { memosApi } from '../api/memos.api';
import { useAuthStore } from '../store/authStore';
import SearchFilterBar from '../components/ui/SearchFilterBar';
import ApprovalActionModal from '../components/requests/ApprovalActionModal';
import { Role, StageStatus, type StageActionType } from '../types/enums';
import { formatDate } from '../utils/formatDate';
import { toastSuccess, toastApiError } from '../utils/toast';
import type { RequestResponseDto } from '../types/request.types';
import type { MemoDto } from '../types/memo.types';

export default function Approvals() {
  const navigate = useNavigate();
  const role = useAuthStore((state) => state.role);

  const [requests, setRequests] = useState<RequestResponseDto[]>([]);
  const [memos, setMemos] = useState<MemoDto[]>([]);
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [activeRequest, setActiveRequest] = useState<RequestResponseDto | null>(null);
  const [activeMemo, setActiveMemo] = useState<MemoDto | null>(null);
  const [modalAction, setModalAction] = useState<StageActionType | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // const loadPending = useCallback(async () => {
  //   setIsLoading(true);
  //   try {
  //     const [requestData, memoData] = await Promise.all([
  //       requestsApi.getPendingForRole(),
  //       memosApi.getPendingForRole(),
  //     ]);
  //     setRequests(requestData);
  //     setMemos(memoData);
  //   } catch (error) {
  //     console.error('Failed to load pending approvals:', error);
  //   } finally {
  //     setIsLoading(false);
  //   }
  // }, []);

  const loadPending = useCallback(async () => {
  setIsLoading(true);
  try {
    const [requestResult, memoResult] = await Promise.allSettled([
      requestsApi.getPendingForRole(),
      memosApi.getPendingForRole(),
    ]);

    if (requestResult.status === 'fulfilled') {
      setRequests(requestResult.value);
    } else {
      console.error('Failed to load pending requests:', requestResult.reason);
    }

    if (memoResult.status === 'fulfilled') {
      setMemos(memoResult.value);
    } else {
      console.error('Failed to load pending memos:', memoResult.reason);
    }

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

  // ─── Request actions ──────────────────────────────────────

  function openRequestAction(request: RequestResponseDto, action: StageActionType) {
    setActiveRequest(request);
    setActiveMemo(null);
    setModalAction(action);
  }

  async function handleConfirm(comment: string, inStock?: boolean) {
    if (!modalAction) return;
    setIsSubmitting(true);

    try {
      if (activeRequest) {
        await requestsApi.processAction(activeRequest.id, {
          action: modalAction,
          comment: comment || undefined,
          inStock,
        });
      } else if (activeMemo) {
        await memosApi.processAction(activeMemo.id, {
          action: modalAction,
          comment: comment || undefined,
        });
      }

      setModalAction(null);
      setActiveRequest(null);
      setActiveMemo(null);

      toastSuccess(
        modalAction === StageStatus.APPROVED ? 'Approved successfully' : 'Rejected'
      );
      loadPending();
    } catch (error) {
      toastApiError(error, 'Failed to process action');
    } finally {
      setIsSubmitting(false);
    }
  }

  // ─── Filter ───────────────────────────────────────────────

  const filteredRequests = requests.filter(
    (r) =>
      r.title.toLowerCase().includes(search.toLowerCase()) ||
      r.referenceNumber.toLowerCase().includes(search.toLowerCase()) ||
      r.initiatorName.toLowerCase().includes(search.toLowerCase())
  );

  const filteredMemos = memos.filter(
    (m) =>
      m.title.toLowerCase().includes(search.toLowerCase()) ||
      m.referenceNumber.toLowerCase().includes(search.toLowerCase()) ||
      m.authorName.toLowerCase().includes(search.toLowerCase())
  );

  const totalCount = filteredRequests.length + filteredMemos.length;

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
      ) : totalCount === 0 ? (
        <div className="border border-dashed border-gray-200 rounded-xl p-12 text-center text-gray-400 text-sm">
          Nothing waiting on your approval right now.
        </div>
      ) : (
        <div className="space-y-8">
          {/* Requests section */}
          {filteredRequests.length > 0 && (
            <div>
              <h2 className="text-lg font-bold text-teal mb-4">
                Requests ({filteredRequests.length})
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredRequests.map((request) => (
                  <RequestApprovalCard
                    key={request.id}
                    request={request}
                    isLogistics={role === Role.LOGISTICS}
                    onViewDetails={() => navigate(`/requests/${request.id}`)}
                    onApprove={() => openRequestAction(request, StageStatus.APPROVED)}
                    onReject={() => openRequestAction(request, StageStatus.REJECTED)}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Memos section */}
          {filteredMemos.length > 0 && (
            <div>
              <h2 className="text-lg font-bold text-teal mb-4">
                Memos ({filteredMemos.length})
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredMemos.map((memo) => (
                  <MemoApprovalCard
                    key={memo.id}
                    memo={memo}
                    onViewDetails={() => navigate(`/memos/${memo.id}`)}
                    onApprove={() => {
                      setActiveMemo(memo);
                      setActiveRequest(null);
                      setModalAction(StageStatus.APPROVED);
                    }}
                    onReject={() => {
                      setActiveMemo(memo);
                      setActiveRequest(null);
                      setModalAction(StageStatus.REJECTED);
                    }}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      <ApprovalActionModal
        isOpen={modalAction !== null}
        action={modalAction}
        isLogistics={role === Role.LOGISTICS && activeRequest !== null}
        onClose={() => {
          setModalAction(null);
          setActiveRequest(null);
          setActiveMemo(null);
        }}
        onConfirm={handleConfirm}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}

// ─── Request Approval Card ────────────────────────────────

function RequestApprovalCard({
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
            {amount
              ? `${amount.toLocaleString()} Rwf`
              : request.referenceNumber}
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

// ─── Memo Approval Card ───────────────────────────────────

function MemoApprovalCard({
  memo,
  onViewDetails,
  onApprove,
  onReject,
}: {
  memo: MemoDto;
  onViewDetails: () => void;
  onApprove: () => void;
  onReject: () => void;
}) {
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
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <path d="M7 8h10M7 12h10M7 16h6" />
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
          <p className="text-xs text-gray-400">Author</p>
          <p className="font-medium text-gray-900">{memo.authorName}</p>
        </div>
        <div>
          <p className="text-xs text-gray-400">Date</p>
          <p className="font-medium text-gray-900">
            {formatDate(memo.createdAt)}
          </p>
        </div>
        <div className="col-span-2">
          <p className="text-xs text-gray-400">Reference</p>
          <p className="font-medium text-gray-900">{memo.referenceNumber}</p>
        </div>
      </div>

      <p className="text-sm text-gray-700 mb-4 line-clamp-2">{memo.title}</p>

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