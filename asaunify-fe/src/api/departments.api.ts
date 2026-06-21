import api from './api';
import type { Department } from '../types/user.types';

export const departmentsApi = {
  createDepartment: async (
    name: string,
    headUserId?: string
  ): Promise<Department> => {
    const response = await api.post<Department>('/departments', {
      name,
      headUserId,
    });
    return response.data;
  },

  getAllDepartments: async (): Promise<Department[]> => {
    const response = await api.get<Department[]>('/departments');
    return response.data;
  },

  getDepartmentById: async (id: string): Promise<Department> => {
    const response = await api.get<Department>(
      `/departments/${id}`
    );
    return response.data;
  },

  updateDepartment: async (
    id: string,
    name?: string,
    headUserId?: string
  ): Promise<Department> => {
    const response = await api.patch<Department>(
      `/departments/${id}`,
      { name, headUserId }
    );
    return response.data;
  },

  deleteDepartment: async (id: string): Promise<void> => {
    await api.delete(`/departments/${id}`);
  },
};