import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Send } from "lucide-react";
import { memosApi } from "../../api/memos.api";
import Button from "../../components/ui/Button";
import { Role } from "../../types/enums";
import { toastApiError, toastSuccess } from "../../utils/toast";

const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: Role.ADMIN, label: "Admin" },
  { value: Role.DEPARTMENT_HEAD, label: "Department Head" },
  { value: Role.LOGISTICS, label: "Logistics" },
  { value: Role.PROCUREMENT, label: "Procurement" },
  { value: Role.FLEET_MANAGER, label: "Fleet Manager" },
  { value: Role.MSME_OFFICER, label: "MSME Officer" },
  { value: Role.RM, label: "Regional Manager" },
  { value: Role.CREDIT_OFFICER, label: "Credit Officer" },
  { value: Role.AUDITOR, label: "Auditor" },
];

export default function NewMemo() {
  const navigate = useNavigate();

  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [selectedRoles, setSelectedRoles] = useState<Role[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function toggleRole(role: Role) {
    setSelectedRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role],
    );
  }

  async function handleSend() {
    if (!title.trim()) return setError("Please enter a title");
    if (!content.trim()) return setError("Please enter memo content");
    if (selectedRoles.length === 0)
      return setError("Please select at least one approver role");

    setError(null);
    setIsSubmitting(true);
    try {
      const memo = await memosApi.createMemo({
        title,
        content,
        approverRoles: selectedRoles,
      });
      toastSuccess('Memo sent successfully');
      navigate(`/memos/${memo.id}`);
    } catch (err) {
      toastApiError(err,"Failed to create memo");
      setError("Something went wrong while sending your memo.");
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

      {error && (
        <div className="mb-6 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
          {error}
        </div>
      )}

      <div className="space-y-6">
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

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Body
          </label>
          <textarea
            placeholder="Write here ..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={8}
            className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-3">
            Select Approvers
          </label>
          <div className="grid grid-cols-3 gap-3">
            {ROLE_OPTIONS.map((option) => (
              <label
                key={option.value}
                className={`flex items-center gap-2 px-4 py-3 border rounded-lg text-sm cursor-pointer transition-colors ${
                  selectedRoles.includes(option.value)
                    ? "border-primary bg-primary/5 text-primary font-medium"
                    : "border-gray-200 text-gray-600"
                }`}
              >
                <input
                  type="checkbox"
                  checked={selectedRoles.includes(option.value)}
                  onChange={() => toggleRole(option.value)}
                  className="w-4 h-4 accent-primary"
                />
                {option.label}
              </label>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
