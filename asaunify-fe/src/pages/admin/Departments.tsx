import { useCallback, useEffect, useState } from "react";
import { Plus, X } from "lucide-react";
import { departmentsApi } from "../../api/departments.api";
import { usersApi } from "../../api/users.api";
import Button from "../../components/ui/Button";
import DataTable, { type Column } from "../../components/ui/DataTable";
import { Role } from "../../types/enums";
import type { Department, UserDto } from "../../types/user.types";
import { toastApiError, toastSuccess } from "../../utils/toast";

export default function AdminDepartments() {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [heads, setHeads] = useState<UserDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<Department | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [deptData, headsData] = await Promise.all([
        departmentsApi.getAllDepartments(),
        usersApi.getUsersByRole(Role.DEPARTMENT_HEAD),
      ]);
      setDepartments(deptData);
      setHeads(headsData);
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

  function headName(headUserId: string | null): string {
    if (!headUserId) return "Unassigned";
    return heads.find((h) => h.id === headUserId)?.fullName ?? "Unassigned";
  }

  // const columns: Column<Department>[] = [
  //   { header: 'Department Name', accessor: (d) => d.name },
  //   { header: 'Department Head', accessor: (d) => headName(d.headUserId) },
  // ];

  const columns: Column<Department>[] = [
    { header: "Department Name", accessor: (d) => d.name },
    { header: "Department Head", accessor: (d) => headName(d.headUserId) },
    {
      header: "Actions",
      accessor: (d) => (
        <button
          onClick={() => setEditingDept(d)}
          className="text-primary font-medium underline hover:text-primary-dark text-sm"
        >
          Assign Head
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
        <CreateDepartmentModal
          heads={heads}
          onClose={() => setIsModalOpen(false)}
          onCreated={() => {
            setIsModalOpen(false);
            loadData();
          }}
        />
      )}

      {editingDept && (
        <AssignHeadModal
          department={editingDept}
          heads={heads}
          onClose={() => setEditingDept(null)}
          onAssigned={() => {
            setEditingDept(null);
            loadData();
          }}
        />
      )}
    </div>
  );
}

function CreateDepartmentModal({
  heads,
  onClose,
  onCreated,
}: {
  heads: UserDto[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const [name, setName] = useState("");
  const [headUserId, setHeadUserId] = useState("");
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
      await departmentsApi.createDepartment(name, headUserId || undefined);
      toastSuccess("Department created successfully");
      onCreated();
    } catch (err) {
      toastApiError(err, "Failed to create department");
      setError("Failed to create department. Name may already exist.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">New Department</h3>
          <button
            title="close"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-3">
          <input
            type="text"
            placeholder="Department Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <select
            title="select"
            value={headUserId}
            onChange={(e) => setHeadUserId(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            <option value="">No head assigned yet</option>
            {heads.map((h) => (
              <option key={h.id} value={h.id}>
                {h.fullName}
              </option>
            ))}
          </select>
          <p className="text-xs text-gray-400">
            Only users with the Department Head role appear here. Create the
            user first if they don't show up.
          </p>
        </div>

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
            {isSubmitting ? "Creating..." : "Create Department"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function AssignHeadModal({
  department,
  heads,
  onClose,
  onAssigned,
}: {
  department: Department;
  heads: UserDto[];
  onClose: () => void;
  onAssigned: () => void;
}) {
  const [headUserId, setHeadUserId] = useState(department.headUserId ?? "");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit() {
    setIsSubmitting(true);
    try {
      await departmentsApi.updateDepartment(
        department.id,
        department.name,
        headUserId || undefined,
      );
      toastSuccess("Department head assigned successfully");
      onAssigned();
    } catch (err) {
      toastApiError(err, "Failed to assign head");
      setError("Failed to update department head.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">
            Assign Head — {department.name}
          </h3>
          <button
            title="Close"
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={20} />
          </button>
        </div>

        <select
          title="Head User"
          value={headUserId}
          onChange={(e) => setHeadUserId(e.target.value)}
          className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 mb-2"
        >
          <option value="">No head assigned</option>
          {heads.map((h) => (
            <option key={h.id} value={h.id}>
              {h.fullName}
            </option>
          ))}
        </select>

        <p className="text-xs text-gray-400 mb-4">
          Only users with the Department Head role appear here.
        </p>

        {error && <p className="text-sm text-status-rejected mb-3">{error}</p>}

        <div className="flex gap-3">
          <Button variant="ghost" onClick={onClose} className="flex-1">
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1"
          >
            {isSubmitting ? "Saving..." : "Save"}
          </Button>
        </div>
      </div>
    </div>
  );
}
