import api from './api';
import type { Department } from '../types/user.types';

export const departmentsApi = {
  // Head is not set here — it is derived from the user's role/department.
  createDepartment: async (name: string): Promise<Department> => {
    const response = await api.post<Department>('/departments', { name });
    return response.data;
  },

  getAllDepartments: async (): Promise<Department[]> => {
    const response = await api.get<Department[]>('/departments');
    return response.data;
  },

  getDepartmentById: async (id: string): Promise<Department> => {
    const response = await api.get<Department>(`/departments/${id}`);
    return response.data;
  },

  updateDepartment: async (id: string, name: string): Promise<Department> => {
    const response = await api.patch<Department>(`/departments/${id}`, { name });
    return response.data;
  },

  deleteDepartment: async (id: string): Promise<void> => {
    await api.delete(`/departments/${id}`);
  },
};
