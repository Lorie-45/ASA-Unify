import api from './api';
import type { ActionType } from '../types/enums';

// ─── Dashboard summary shape — mirrors DashboardSummaryDTO ──

export interface DashboardSummaryDto {
  totalRequests: number;
  totalPending: number;
  totalApproved: number;
  totalCompleted: number;
  totalRejected: number;
  onTimeRatePercent: number;
  pendingByType: Record<string, number>;
  averageCompletionMinutesByRole: Record<string, number>;
}

export interface UserActivityDto {
  userId: string;
  fullName: string;
  email: string;
  role: string;
  departmentName: string | null;
  casesInitiated: number;
  tasksCompleted: number;
  averageTaskMinutes: number;
}

export interface TaskAssignmentDto {
  stageId: string;
  requestReferenceNumber: string;
  requestTitle: string;
  assignedRole: string;
  status: string;
  assignedAt: string;
  actedAt: string | null;
  durationMinutes: number | null;
  comment: string | null;
}

export interface AuditLogDto {
  id: string;
  timestamp: string;
  userId: string | null;
  userFullName: string | null;
  userRole: string | null;
  actionType: string;
  objectType: string | null;
  objectId: string | null;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
  module: string;
}

export interface LoginHistoryDto {
  id: string;
  userId: string;
  userFullName: string;
  userRole: string;
  loginAt: string;
  logoutAt: string | null;
  sessionDurationMinutes: number | null;
  ipAddress: string | null;
  browser: string | null;
  device: string | null;
  loginStatus: string;
}

export const reportsApi = {
  // ─── Dashboard ──────────────────────────────────────────

  getDashboard: async (from?: string, to?: string): Promise<DashboardSummaryDto> => {
    const response = await api.get<DashboardSummaryDto>(
      '/reports/dashboard',
      { params: { from, to } }
    );
    return response.data;
  },

  // ─── User Activity ──────────────────────────────────────

  getUserActivity: async (
    from?: string,
    to?: string
  ): Promise<UserActivityDto[]> => {
    const response = await api.get<UserActivityDto[]>(
      '/reports/users/activity',
      { params: { from, to } }
    );
    return response.data;
  },

  getSingleUserActivity: async (
    userId: string,
    from?: string,
    to?: string
  ): Promise<UserActivityDto> => {
    const response = await api.get<UserActivityDto>(
      `/reports/users/${userId}/activity`,
      { params: { from, to } }
    );
    return response.data;
  },

  getTaskAssignments: async (
    userId: string,
    from?: string,
    to?: string
  ): Promise<TaskAssignmentDto[]> => {
    const response = await api.get<TaskAssignmentDto[]>(
      `/reports/users/${userId}/tasks`,
      { params: { from, to } }
    );
    return response.data;
  },

  // ─── Audit ──────────────────────────────────────────────

  getAuditLogs: async (params: {
    userId?: string;
    actionType?: ActionType;
    module?: string;
    objectType?: string;
    from?: string;
    to?: string;
  }): Promise<AuditLogDto[]> => {
    const response = await api.get<AuditLogDto[]>(
      '/reports/audit/logs',
      { params }
    );
    return response.data;
  },

  getObjectHistory: async (
    objectType: string,
    objectId: string
  ): Promise<AuditLogDto[]> => {
    const response = await api.get<AuditLogDto[]>(
      `/reports/audit/history/${objectType}/${objectId}`
    );
    return response.data;
  },

  getRecentLogs: async (limit = 20): Promise<AuditLogDto[]> => {
    const response = await api.get<AuditLogDto[]>(
      '/reports/audit/recent',
      { params: { limit } }
    );
    return response.data;
  },

  getLoginHistory: async (
    userId?: string,
    from?: string,
    to?: string
  ): Promise<LoginHistoryDto[]> => {
    const response = await api.get<LoginHistoryDto[]>(
      '/reports/audit/logins',
      { params: { userId, from, to } }
    );
    return response.data;
  },

  getFailedLogins: async (
    from?: string,
    to?: string
  ): Promise<LoginHistoryDto[]> => {
    const response = await api.get<LoginHistoryDto[]>(
      '/reports/audit/failed-logins',
      { params: { from, to } }
    );
    return response.data;
  },

  // ─── Export ─────────────────────────────────────────────

  exportCases: async (params: {
    format: 'excel' | 'csv' | 'pdf';
    status?: string;
    type?: string;
    from?: string;
    to?: string;
  }): Promise<Blob> => {
    const response = await api.get('/reports/export', {
      params,
      responseType: 'blob',
    });
    return response.data;
  },
};