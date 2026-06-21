import { useAuthStore } from '../store/authStore';
import { Role, NON_INITIATOR_ROLES, CANCEL_AUTHORIZED_ROLES } from '../types/enums';

const APPROVER_ROLES: Role[] = [
  Role.DEPARTMENT_HEAD,
  Role.LOGISTICS,
  Role.PROCUREMENT,
  Role.FLEET_MANAGER,
  Role.MSME_OFFICER,
  Role.RM,
  Role.CREDIT_OFFICER,
];

export function usePermissions() {
  const role = useAuthStore((state) => state.role);

  return {
    role,
    canInitiateRequests: role ? !NON_INITIATOR_ROLES.includes(role) : false,
    canApprove: role ? APPROVER_ROLES.includes(role) : false,
    canCancelRequests: role ? CANCEL_AUTHORIZED_ROLES.includes(role) : false,
    isAdmin: role === Role.ADMIN,
    isAuditor: role === Role.AUDITOR,
    isDepartmentHead: role === Role.DEPARTMENT_HEAD,
    isDriver: role === Role.DRIVER,
  };
}