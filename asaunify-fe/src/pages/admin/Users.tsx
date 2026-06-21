import { useCallback, useEffect, useState } from "react";
import { Plus, X } from "lucide-react";
import { usersApi } from "../../api/users.api";
import { departmentsApi } from "../../api/departments.api";
import Button from "../../components/ui/Button";
import SearchFilterBar from "../../components/ui/SearchFilterBar";
import DataTable, { type Column } from "../../components/ui/DataTable";
import { Role } from "../../types/enums";
import type { UserDto, Department } from "../../types/user.types";

const ROLE_OPTIONS = Object.values(Role);

const NO_DEPARTMENT_ROLES: Role[] = [Role.ADMIN, Role.AUDITOR];

export default function AdminUsers() {
  const [users, setUsers] = useState<UserDto[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [usersData, deptData] = await Promise.all([
        usersApi.getAllUsers(),
        departmentsApi.getAllDepartments(),
      ]);
      setUsers(usersData);
      setDepartments(deptData);
    } catch (error) {
      console.error("Failed to load users:", error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional fetch-on-mount
    loadData();
  }, [loadData]);

  async function handleDeactivate(id: string) {
    if (
      !window.confirm(
        "Deactivate this user? They will no longer be able to log in.",
      )
    )
      return;
    try {
      await usersApi.deactivateUser(id);
      loadData();
    } catch (error) {
      console.error("Failed to deactivate user:", error);
    }
  }

  const filtered = users.filter(
    (u) =>
      u.fullName.toLowerCase().includes(search.toLowerCase()) ||
      u.email.toLowerCase().includes(search.toLowerCase()),
  );

  const columns: Column<UserDto>[] = [
    { header: "Name", accessor: (u) => u.fullName },
    { header: "Email", accessor: (u) => u.email },
    { header: "Role", accessor: (u) => formatLabel(u.role) },
    { header: "Department", accessor: (u) => u.departmentName ?? "—" },
    {
      header: "Status",
      accessor: (u) => (
        <span
          className={`text-sm font-semibold ${
            u.isActive ? "text-status-approved" : "text-gray-400"
          }`}
        >
          {u.isActive ? "Active" : "Deactivated"}
        </span>
      ),
    },
    {
      header: "Actions",
      accessor: (u) =>
        u.isActive ? (
          <button
            onClick={() => handleDeactivate(u.id)}
            className="text-status-rejected font-medium hover:underline"
          >
            Deactivate
          </button>
        ) : (
          <span className="text-gray-300">—</span>
        ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">User Management</h1>
        <Button icon={<Plus size={18} />} onClick={() => setIsModalOpen(true)}>
          New User
        </Button>
      </div>

      <SearchFilterBar
        value={search}
        onChange={setSearch}
        placeholder="Search by name or email"
      />

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-12">Loading...</p>
      ) : (
        <DataTable
          columns={columns}
          data={filtered}
          keyExtractor={(u) => u.id}
          emptyMessage="No users found"
        />
      )}

      {isModalOpen && (
        <CreateUserModal
          departments={departments}
          onClose={() => setIsModalOpen(false)}
          onCreated={() => {
            setIsModalOpen(false);
            loadData();
          }}
        />
      )}
    </div>
  );
}

// ─── Create User Modal ─────────────────────────────

// ─── Create User Modal ────────────────────────────────────

function CreateUserModal({
  departments,
  onClose,
  onCreated,
}: {
  departments: Department[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role | "">("");
  const [departmentId, setDepartmentId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const needsDepartment = role && !NO_DEPARTMENT_ROLES.includes(role as Role);

  async function handleSubmit() {
    if (!fullName.trim() || !email.trim() || !password.trim() || !role) {
      setError("Please fill in all required fields");
      return;
    }
    if (needsDepartment && !departmentId) {
      setError("Please select a department for this role");
      return;
    }

    setError(null);
    setIsSubmitting(true);
    try {
      await usersApi.createUser({
        fullName,
        email,
        password,
        role: role as Role,
        departmentId: needsDepartment ? departmentId : undefined,
      });
      onCreated();
    } catch (err) {
      console.error("Failed to create user:", err);
      setError("Failed to create user. Email may already be in use.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl w-full max-w-md p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">New User</h3>
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
            placeholder="Full Name"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <input
            type="password"
            placeholder="Temporary Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          <select
            title="select"
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            <option value="">Select role</option>
            {ROLE_OPTIONS.map((r) => (
              <option key={r} value={r}>
                {formatLabel(r)}
              </option>
            ))}
          </select>

          {needsDepartment && (
            <select
              title="select"
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">Select department</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          )}
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
            {isSubmitting ? "Creating..." : "Create User"}
          </Button>
        </div>
      </div>
    </div>
  );
}

function formatLabel(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}
