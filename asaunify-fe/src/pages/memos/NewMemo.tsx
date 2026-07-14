// import { useState } from "react";
// import { useNavigate } from "react-router-dom";
// import { Send } from "lucide-react";
// import { memosApi } from "../../api/memos.api";
// import Button from "../../components/ui/Button";
// import { Role } from "../../types/enums";
// import { toastApiError, toastSuccess } from "../../utils/toast";

// const ROLE_OPTIONS: { value: Role; label: string }[] = [
//   { value: Role.ADMIN, label: "Admin" },
//   { value: Role.DEPARTMENT_HEAD, label: "Department Head" },
//   { value: Role.LOGISTICS, label: "Logistics" },
//   { value: Role.PROCUREMENT, label: "Procurement" },
//   { value: Role.FLEET_MANAGER, label: "Fleet Manager" },
//   { value: Role.MSME_OFFICER, label: "MSME Officer" },
//   { value: Role.RM, label: "Regional Manager" },
//   { value: Role.CREDIT_OFFICER, label: "Credit Officer" },
//   { value: Role.AUDITOR, label: "Auditor" },
// ];

// export default function NewMemo() {
//   const navigate = useNavigate();

//   const [title, setTitle] = useState("");
//   const [content, setContent] = useState("");
//   const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
//   const [error, setError] = useState<string | null>(null);
//   const [isSubmitting, setIsSubmitting] = useState(false);

//   function toggleRole(role: Role) {
//     setSelectedRoles((prev) =>
//       prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
//     );
//   }

//   async function handleSend() {
//     if (!title.trim()) return setError("Please enter a title");
//     if (!content.trim()) return setError("Please enter memo content");
//     if (selectedRoles.length === 0)
//       return setError("Please select at least one approver role");

//     setError(null);
//     setIsSubmitting(true);
//     try {
//       const memo = await memosApi.createMemo({
//         title,
//         content,
//         approverRoles: selectedRoles,
//       });
//       toastSuccess('Memo sent successfully');
//       navigate(`/memos/${memo.id}`);
//     } catch (err) {
//       toastApiError(err,"Failed to create memo");
//       setError("Something went wrong while sending your memo.");
//     } finally {
//       setIsSubmitting(false);
//     }
//   }

//   return (
//     <div className="max-w-3xl">
//       <div className="flex items-center justify-between mb-8">
//         <h1 className="text-xl font-bold text-gray-900">New Memo</h1>
//         <Button
//           icon={<Send size={16} />}
//           disabled={isSubmitting}
//           onClick={handleSend}
//         >
//           {isSubmitting ? "Sending..." : "Send"}
//         </Button>
//       </div>

//       {error && (
//         <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
//           {error}
//         </div>
//       )}

//       <div className="space-y-6">
//         <div>
//           <label className="block text-sm font-medium text-gray-700 mb-1">
//             Title
//           </label>
//           <input
//             title="Title"
//             type="text"
//             value={title}
//             onChange={(e) => setTitle(e.target.value)}
//             className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
//           />
//         </div>

//         <div>
//           <label className="block text-sm font-medium text-gray-700 mb-1">
//             Body
//           </label>
//           <textarea
//             placeholder="Write here ..."
//             value={content}
//             onChange={(e) => setContent(e.target.value)}
//             rows={8}
//             className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
//           />
//         </div>

//         <div>
//           <label className="block text-sm font-medium text-gray-700 mb-3">
//             Select Approvers
//           </label>
//           <div className="grid grid-cols-3 gap-3">
//             {ROLE_OPTIONS.map((option) => (
//               <label
//                 key={option.value}
//                 className={`flex items-center gap-2 px-4 py-3 border rounded-lg text-sm cursor-pointer transition-colors ${
//                   selectedRoles.includes(option.value)
//                     ? "border-primary bg-primary/5 text-primary font-medium"
//                     : "border-gray-200 text-gray-600"
//                 }`}
//               >
//                 <input
//                   type="checkbox"
//                   checked={selectedRoles.includes(option.value)}
//                   onChange={() => toggleRole(option.value)}
//                   className="w-4 h-4 accent-primary"
//                 />
//                 {option.label}
//               </label>
//             ))}
//           </div>
//         </div>
//       </div>
//     </div>
//   );
// }

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Send } from "lucide-react";
import { memosApi } from "../../api/memos.api";
import { usersApi } from "../../api/users.api";
import Button from "../../components/ui/Button";
import { Role } from "../../types/enums";
import type { UserDto } from "../../types/user.types";
import { toastSuccess, toastApiError, toastError } from "../../utils/toast";

// Roles that can approve memos
const APPROVER_ROLES: Role[] = [
  Role.ADMIN,
  Role.DEPARTMENT_HEAD,
  Role.LOGISTICS,
  Role.PROCUREMENT,
  Role.FLEET_MANAGER,
  Role.MSME_OFFICER,
  Role.RM,
  Role.CREDIT_OFFICER,
  Role.AUDITOR,
];

function formatRole(role: string): string {
  return role
    .toLowerCase()
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

export default function NewMemo() {
  const navigate = useNavigate();

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [selectedApproverIds, setSelectedApproverIds] = useState<string[]>([]);
  const [approvers, setApprovers] = useState<UserDto[]>([]);
  const [isLoadingApprovers, setIsLoadingApprovers] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadApprovers = useCallback(async () => {
    setIsLoadingApprovers(true);
    try {
      const results = await Promise.allSettled(
        APPROVER_ROLES.map((role) => usersApi.getUsersByRole(role)),
      );

      const allUsers = results
        .filter((r) => r.status === "fulfilled")
        .flatMap((r) => (r as PromiseFulfilledResult<UserDto[]>).value);

      setApprovers(allUsers);
    } catch (error) {
      console.error("Failed to load approvers:", error);
    } finally {
      setIsLoadingApprovers(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadApprovers();
  }, [loadApprovers]);

  function toggleApprover(userId: string) {
    setSelectedApproverIds((prev) =>
      prev.includes(userId)
        ? prev.filter((id) => id !== userId)
        : [...prev, userId],
    );
  }

  async function handleSend() {
    if (!title.trim()) {
      toastError("Please enter a title");
      return;
    }
    if (!content.trim()) {
      toastError("Please enter memo content");
      return;
    }
    if (selectedApproverIds.length === 0) {
      toastError("Please select at least one approver");
      return;
    }

    setIsSubmitting(true);
    try {
      const memo = await memosApi.createMemo({
        title,
        content,
        approverIds: selectedApproverIds,
      });
      toastSuccess("Memo sent successfully");
      navigate(`/memos/${memo.id}`);
    } catch (err) {
      toastApiError(err, "Failed to send memo.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-xl font-bold text-gray-900">New Memo</h1>
        <Button
          icon={<Send size={16} />}
          disabled={isSubmitting}
          onClick={handleSend}
        >
          {isSubmitting ? "Sending..." : "Send"}
        </Button>
      </div>

      <div className="space-y-6">
        {/* Title */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Title
          </label>
          <input
            title="Title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        {/* Content */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Body
          </label>
          <textarea
            title="Body text"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={8}
            className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        {/* Approver selection — users not roles */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Select Approvers
            {selectedApproverIds.length > 0 && (
              <span className="ml-2 text-xs font-normal text-primary">
                {selectedApproverIds.length} selected
              </span>
            )}
          </label>

          {isLoadingApprovers ? (
            <p className="text-sm text-gray-400 py-4">Loading approvers...</p>
          ) : approvers.length === 0 ? (
            <p className="text-sm text-gray-400 py-4">
              No approvers available. Create users with management roles first.
            </p>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
              {approvers.map((user) => (
                <label
                  key={user.id}
                  className={`flex items-start gap-3 px-4 py-3 border rounded-xl cursor-pointer transition-colors ${
                    selectedApproverIds.includes(user.id)
                      ? "border-primary bg-primary/5"
                      : "border-gray-200 hover:border-gray-300"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={selectedApproverIds.includes(user.id)}
                    onChange={() => toggleApprover(user.id)}
                    className="w-4 h-4 accent-primary mt-0.5 shrink-0"
                  />
                  <div className="min-w-0">
                    <p
                      className={`text-sm font-medium truncate ${
                        selectedApproverIds.includes(user.id)
                          ? "text-primary"
                          : "text-gray-900"
                      }`}
                    >
                      {user.fullName}
                    </p>
                    <p className="text-xs text-gray-400 truncate">
                      {formatRole(user.role)}
                    </p>
                  </div>
                </label>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
