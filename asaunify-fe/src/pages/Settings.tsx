import { useAuthStore } from '../store/authStore';

function formatLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

export default function Settings() {
  const fullName = useAuthStore((state) => state.fullName);
  const email = useAuthStore((state) => state.email);
  const role = useAuthStore((state) => state.role);
  const departmentName = useAuthStore((state) => state.departmentName);

  return (
    <div className="max-w-xl">
      <h1 className="text-xl font-bold text-gray-900 mb-6">Settings</h1>

      <section className="border border-gray-200 rounded-xl p-6 space-y-4">
        <h2 className="text-lg font-bold text-teal mb-2">Account Information</h2>

        <Field label="Full Name" value={fullName ?? '—'} />
        <Field label="Email" value={email ?? '—'} />
        <Field label="Role" value={role ? formatLabel(role) : '—'} />
        <Field label="Department" value={departmentName ?? 'Not assigned'} />
      </section>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between border-b border-gray-100 pb-3">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm font-medium text-gray-900">{value}</span>
    </div>
  );
}