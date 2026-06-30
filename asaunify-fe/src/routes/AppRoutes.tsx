import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";
import AppShell from "../components/layout/AppShell";
import { Role } from "../types/enums";
import Login from "../pages/Login";
import Dashboard from "../pages/Dashboard";
import RequestList from "../pages/requests/RequestList";
import NewRequest from "../pages/requests/NewRequest";
import RequestDetail from "../pages/requests/RequestDetails";
import Approvals from "../pages/Approvals";
import MemoList from "../pages/memos/MemoList";
import NewMemo from "../pages/memos/NewMemo";
import MemoDetail from "../pages/memos/MemoDetails";
import NotificationsPage from "../pages/Notifications";
import CaseReports from "../pages/reports/CaseReports";
import DashboardReport from "../pages/reports/DashboardReport";
import UserReports from "../pages/reports/UserReports";
import AuditLog from "../pages/reports/AuditLog";
import AdminUsers from "../pages/admin/Users";
import AdminDepartments from "../pages/admin/Departments";
import Settings from "../pages/Settings";
import MyTrips from "../pages/driver/MyTrips";

export default function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public */}
        <Route path="/login" element={<Login />} />

        {/* Protected — wrapped in AppShell (Sidebar + Topbar) */}
        <Route
          element={
            <ProtectedRoute>
              <AppShell />
            </ProtectedRoute>
          }
        >
          <Route path="/dashboard" element={<Dashboard />} />

          {/* Requests */}
          <Route path="/requests" element={<RequestList />} />
          <Route path="/requests/new" element={<NewRequest />} />
          <Route path="/requests/:id" element={<RequestDetail />} />

          {/* Approvals — only for approver-capable roles */}
          <Route
            path="/approvals"
            element={
              <ProtectedRoute
                allowedRoles={[
                  Role.DEPARTMENT_HEAD,
                  Role.LOGISTICS,
                  Role.PROCUREMENT,
                  Role.FLEET_MANAGER,
                  Role.MSME_OFFICER,
                  Role.RM,
                  Role.CREDIT_OFFICER,
                ]}
              >
                <Approvals />
              </ProtectedRoute>
            }
          />

          {/* Memos */}
          <Route path="/memos" element={<MemoList />} />
          <Route path="/memos/new" element={<NewMemo />} />
          <Route path="/memos/:id" element={<MemoDetail />} />

          {/* Notifications */}
          <Route path="/notifications" element={<NotificationsPage />} />

          {/* Reports */}
          <Route path="/reports" element={<CaseReports />} />
          <Route
            path="/reports/dashboard"
            element={
              <ProtectedRoute
                allowedRoles={[Role.ADMIN, Role.AUDITOR, Role.DEPARTMENT_HEAD]}
              >
                <DashboardReport />
              </ProtectedRoute>
            }
          />
          <Route
            path="/reports/users"
            element={
              <ProtectedRoute allowedRoles={[Role.ADMIN, Role.AUDITOR]}>
                <UserReports />
              </ProtectedRoute>
            }
          />
          <Route
            path="/activity-logs"
            element={
              <ProtectedRoute allowedRoles={[Role.ADMIN, Role.AUDITOR]}>
                <AuditLog />
              </ProtectedRoute>
            }
          />

          {/* Admin only */}
          <Route
            path="/admin/users"
            element={
              <ProtectedRoute allowedRoles={[Role.ADMIN]}>
                <AdminUsers />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/departments"
            element={
              <ProtectedRoute allowedRoles={[Role.ADMIN]}>
                <AdminDepartments />
              </ProtectedRoute>
            }
          />

          <Route
            path="/trips"
            element={
              <ProtectedRoute allowedRoles={[Role.DRIVER]}>
                <MyTrips />
              </ProtectedRoute>
            }
          />

          <Route path="/settings" element={<Settings />} />
        </Route>

        {/* Fallback */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
