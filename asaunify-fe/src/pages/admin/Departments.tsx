import { useCallback, useEffect, useState } from "react";
import { Plus, X } from "lucide-react";
import { departmentsApi } from "../../api/departments.api";
import Button from "../../components/ui/Button";
import DataTable, { type Column } from "../../components/ui/DataTable";
import type { Department } from "../../types/user.types";
import { toastApiError, toastSuccess } from "../../utils/toast";

export default function AdminDepartments() {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<Department | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      setDepartments(await departmentsApi.getAllDepartments());
    } catch (error) {
      console.error("Failed to load departments:", error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadData();
  }, [loadData]);

  const columns: Column<Department>[] = [
    { header: "Department Name", accessor: (d) => d.name },
    {
      header: "Department Head",
      accessor: (d) => d.headName ?? "Unassigned",
    },
    {
      header: "Actions",
      accessor: (d) => (
        <button
          onClick={() => setEditingDept(d)}
          className="text-primary font-medium underline hover:text-primary-dark text-sm"
        >
          Rename
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Departments</h1>
        <Button icon={<Plus size={18} />} onClick={() => setIsModalOpen(true)}>
          New Department
        </Button>
      </div>

      <p className="text-xs text-gray-400">
        A department&apos;s head is set by giving a user the Department Head role
        in that department (on the Users page) — it updates here automatically.
      </p>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <DataTable
          columns={columns}
          data={departments}
          keyExtractor={(d) => d.id}
          emptyMessage="No departments yet"
        />
      )}

      {isModalOpen && (
        <DepartmentFormModal
          title="New Department"
          submitLabel="Create Department"
          onClose={() => setIsModalOpen(false)}
          onSubmit={async (name) => {
            await departmentsApi.createDepartment(name);
            toastSuccess("Department created successfully");
            setIsModalOpen(false);
            loadData();
          }}
        />
      )}

      {editingDept && (
        <DepartmentFormModal
          title={`Rename — ${editingDept.name}`}
          submitLabel="Save"
          initialName={editingDept.name}
          onClose={() => setEditingDept(null)}
          onSubmit={async (name) => {
            await departmentsApi.updateDepartment(editingDept.id, name);
            toastSuccess("Department updated successfully");
            setEditingDept(null);
            loadData();
          }}
        />
      )}
    </div>
  );
}

function DepartmentFormModal({
  title,
  submitLabel,
  initialName = "",
  onClose,
  onSubmit,
}: {
  title: string;
  submitLabel: string;
  initialName?: string;
  onClose: () => void;
  onSubmit: (name: string) => Promise<void>;
}) {
  const [name, setName] = useState(initialName);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit() {
    if (!name.trim()) {
      setError("Please enter a department name");
      return;
    }
    setError(null);
    setIsSubmitting(true);
    try {
      await onSubmit(name.trim());
    } catch (err) {
      toastApiError(err, "Failed to save department");
      setError("Failed to save department. The name may already exist.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          <button
            title="close"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={20} />
          </button>
        </div>

        <input
          type="text"
          placeholder="Department Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
        />

        {error && <p className="text-sm text-status-rejected mt-3">{error}</p>}

        <div className="flex gap-3 mt-6">
          <Button variant="ghost" onClick={onClose} className="flex-1">
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1"
          >
            {isSubmitting ? "Saving..." : submitLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
