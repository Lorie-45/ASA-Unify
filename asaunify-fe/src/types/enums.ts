export const Role = {
  EMPLOYEE: 'EMPLOYEE',
  ADMIN: 'ADMIN',
  DEPARTMENT_HEAD: 'DEPARTMENT_HEAD',
  LOGISTICS: 'LOGISTICS',
  PROCUREMENT: 'PROCUREMENT',
  FLEET_MANAGER: 'FLEET_MANAGER',
  DRIVER: 'DRIVER',
  LOAN_OFFICER: 'LOAN_OFFICER',
  MSME_OFFICER: 'MSME_OFFICER',
  RM: 'RM',
  CREDIT_OFFICER: 'CREDIT_OFFICER',
  AUDITOR: 'AUDITOR',
} as const;
export type Role = (typeof Role)[keyof typeof Role];

export const RequestType = {
  EQUIPMENT: 'EQUIPMENT',
  VEHICLE: 'VEHICLE',
  LOAN: 'LOAN',
} as const;
export type RequestType = (typeof RequestType)[keyof typeof RequestType];

export const RequestStatus = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  COMPLETED: 'COMPLETED',
} as const;
export type RequestStatus = (typeof RequestStatus)[keyof typeof RequestStatus];

export const StageStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const;
export type StageStatus = (typeof StageStatus)[keyof typeof StageStatus];

export type StageActionType = typeof StageStatus.APPROVED | typeof StageStatus.REJECTED;


export const ActionType = {
  LOGIN: 'LOGIN',
  LOGOUT: 'LOGOUT',
  LOGIN_FAILED: 'LOGIN_FAILED',
  REQUEST_CREATED: 'REQUEST_CREATED',
  REQUEST_SUBMITTED: 'REQUEST_SUBMITTED',
  REQUEST_APPROVED: 'REQUEST_APPROVED',
  REQUEST_REJECTED: 'REQUEST_REJECTED',
  REQUEST_COMPLETED: 'REQUEST_COMPLETED',
  REQUEST_UPDATED: 'REQUEST_UPDATED',
  STAGE_APPROVED: 'STAGE_APPROVED',
  STAGE_REJECTED: 'STAGE_REJECTED',
  DRIVER_ASSIGNED: 'DRIVER_ASSIGNED',
  TRIP_SEEN: 'TRIP_SEEN',
  MEMO_CREATED: 'MEMO_CREATED',
  MEMO_APPROVED: 'MEMO_APPROVED',
  MEMO_REJECTED: 'MEMO_REJECTED',
  USER_CREATED: 'USER_CREATED',
  USER_UPDATED: 'USER_UPDATED',
  USER_DEACTIVATED: 'USER_DEACTIVATED',
  REQUEST_CANCELLED: 'REQUEST_CANCELLED',
} as const;
export type ActionType = (typeof ActionType)[keyof typeof ActionType];

export const NON_INITIATOR_ROLES: Role[] = [Role.DRIVER, Role.AUDITOR];

export const CANCEL_AUTHORIZED_ROLES: Role[] = [
  Role.ADMIN,
  Role.DEPARTMENT_HEAD,
  Role.RM,
  Role.MSME_OFFICER,
];