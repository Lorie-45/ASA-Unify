import type {
  MemoDto,
  CreateMemoRequest,
  MemoApprovalActionDto,
} from '../types/memo.types';
import api from './api';

export const memosApi = {
  createMemo: async (dto: CreateMemoRequest): Promise<MemoDto> => {
    const response = await api.post<MemoDto>('/memos', dto);
    return response.data;
  },

  processAction: async (
    id: string,
    dto: MemoApprovalActionDto
  ): Promise<MemoDto> => {
    const response = await api.post<MemoDto>(
      `/memos/${id}/action`,
      dto
    );
    return response.data;
  },

  getMyMemos: async (): Promise<MemoDto[]> => {
    const response = await api.get<MemoDto[]>('/memos/my');
    return response.data;
  },

  getPendingForRole: async (): Promise<MemoDto[]> => {
    const response = await api.get<MemoDto[]>('/memos/pending');
    return response.data;
  },

  getAllMemos: async (): Promise<MemoDto[]> => {
    const response = await api.get<MemoDto[]>('/memos');
    return response.data;
  },

  getMemoById: async (id: string): Promise<MemoDto> => {
    const response = await api.get<MemoDto>(`/memos/${id}`);
    return response.data;
  },
};