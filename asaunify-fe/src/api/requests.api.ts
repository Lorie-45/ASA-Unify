import type {
  RequestResponseDto,
  CreateRequestDto,
  ApprovalActionDto,
  AssignDriverDto,
} from '../types/request.types';
import type { RequestStatus, RequestType } from '../types/enums';
import api from './api';

export const requestsApi = {
  // ─── Create & Submit ────────────────────────────────────

  createDraft: async (dto: CreateRequestDto): Promise<RequestResponseDto> => {
    const response = await api.post<RequestResponseDto>(
      '/requests',
      dto
    );
    return response.data;
  },

  submitRequest: async (id: string): Promise<RequestResponseDto> => {
    const response = await api.post<RequestResponseDto>(
      `/requests/${id}/submit`
    );
    return response.data;
  },

  // ─── Approve / Reject ───────────────────────────────────

  processAction: async (
    id: string,
    dto: ApprovalActionDto
  ): Promise<RequestResponseDto> => {
    const response = await api.post<RequestResponseDto>(
      `/requests/${id}/action`,
      dto
    );
    return response.data;
  },

  // ─── Cancel ──────────────────────────────────────────────

  cancelRequest: async (
    id: string,
    reason: string
  ): Promise<RequestResponseDto> => {
    const response = await api.post<RequestResponseDto>(
      `/requests/${id}/cancel`,
      { reason }
    );
    return response.data;
  },

  // ─── Vehicle-specific ────────────────────────────────────

  assignDriver: async (id: string, dto: AssignDriverDto): Promise<void> => {
    await api.post(`/requests/${id}/assign-driver`, dto);
  },

  markTripSeen: async (id: string): Promise<void> => {
    await api.post(`/requests/${id}/seen`);
  },

  // ─── Queries ─────────────────────────────────────────────

  getMyRequests: async (): Promise<RequestResponseDto[]> => {
    const response = await api.get<RequestResponseDto[]>(
      '/requests/my'
    );
    return response.data;
  },

  getDepartmentRequests: async (): Promise<RequestResponseDto[]> => {
    const response = await api.get<RequestResponseDto[]>(
      '/requests/department'
    );
    return response.data;
  },

  getPendingForRole: async (): Promise<RequestResponseDto[]> => {
    const response = await api.get<RequestResponseDto[]>(
      '/requests/pending'
    );
    return response.data;
  },

  getAllRequests: async (): Promise<RequestResponseDto[]> => {
    const response = await api.get<RequestResponseDto[]>(
      '/requests'
    );
    return response.data;
  },

  getRequestById: async (id: string): Promise<RequestResponseDto> => {
    const response = await api.get<RequestResponseDto>(
      `/requests/${id}`
    );
    return response.data;
  },

  // ─── Reports — Case views (scoped by role server-side) ──

  getCases: async (params: {
    status?: RequestStatus;
    type?: RequestType;
    from?: string;
    to?: string;
    scope: 'all' | 'my' | 'department' | 'completed' | 'rejected' | 'overdue';
  }): Promise<RequestResponseDto[]> => {
    const { scope, ...query } = params;
    const response = await api.get<RequestResponseDto[]>(
      `/reports/cases/${scope}`,
      { params: query }
    );
    return response.data;
  },
};