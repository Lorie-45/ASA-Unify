import { NavLink } from 'react-router-dom';
import {
  LayoutGrid,
  ClipboardList,
  FileText,
  Settings,
  LogOut,
  CheckSquare,
  BarChart3,
  Users,
  Building2,
  ScrollText,
} from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { Role } from '../../types/enums';

interface NavItem {
  label: string;
  to: string;
  icon: React.ReactNode;
  roles?: Role[]; // undefined = visible to all roles
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard', icon: <LayoutGrid size={20} /> },
  {
    label: 'Approvals',
    to: '/approvals',
    icon: <CheckSquare size={20} />,
    roles: [
      Role.DEPARTMENT_HEAD,
      Role.LOGISTICS,
      Role.PROCUREMENT,
      Role.FLEET_MANAGER,
      Role.MSME_OFFICER,
      Role.RM,
      Role.CREDIT_OFFICER,
    ],
  },
  {
    label: 'Requests',
    to: '/requests',
    icon: <ClipboardList size={20} />,
    roles: [
      Role.EMPLOYEE,
      Role.ADMIN,
      Role.DEPARTMENT_HEAD,
      Role.LOGISTICS,
      Role.PROCUREMENT,
      Role.FLEET_MANAGER,
      Role.LOAN_OFFICER,
      Role.MSME_OFFICER,
      Role.RM,
      Role.CREDIT_OFFICER,
    ],
  },
  {
    label: 'Memo',
    to: '/memos',
    icon: <FileText size={20} />,
    roles: [
      Role.EMPLOYEE,
      Role.ADMIN,
      Role.DEPARTMENT_HEAD,
      Role.LOGISTICS,
      Role.PROCUREMENT,
      Role.FLEET_MANAGER,
      Role.LOAN_OFFICER,
      Role.MSME_OFFICER,
      Role.RM,
      Role.CREDIT_OFFICER,
    ],
  },
  {
    label: 'Activity Logs',
    to: '/activity-logs',
    icon: <ScrollText size={20} />,
    roles: [Role.AUDITOR, Role.ADMIN],
  },
  { label: 'Reports', to: '/reports', icon: <BarChart3 size={20} /> },
  {
    label: 'User Management',
    to: '/admin/users',
    icon: <Users size={20} />,
    roles: [Role.ADMIN],
  },
  {
    label: 'Departments',
    to: '/admin/departments',
    icon: <Building2 size={20} />,
    roles: [Role.ADMIN],
  },
  { label: 'Settings', to: '/settings', icon: <Settings size={20} /> },
];

export default function Sidebar() {
  const role = useAuthStore((state) => state.role);
  const logout = useAuthStore((state) => state.logout);

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (role && item.roles.includes(role))
  );

  const handleLogout = () => {
    logout();
    window.location.href = '/login';
  };

  return (
    <aside className="w-64 h-screen flex flex-col border-r border-gray-200 bg-white sticky top-0">
      {/* Logo */}
      <div className="px-6 py-6">
        <img src="/asa-logo-normal.png" alt="ASA International" className="h-10" />
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 space-y-1">
        {visibleItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary text-white'
                  : 'text-gray-700 hover:bg-gray-100'
              }`
            }
          >
            {item.icon}
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div className="border-t border-gray-200 px-3 py-4">
        <button
          onClick={handleLogout}
          className="flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium text-red-500 hover:bg-red-50 w-full transition-colors"
        >
          <LogOut size={20} />
          Logout
        </button>
      </div>
    </aside>
  );
}