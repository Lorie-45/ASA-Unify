import api from './api';
import type {
  UserDto,
  CreateUserRequest,
  UpdateUserRequest,
} from '../types/user.types';
import type { Role } from '../types/enums';

export const usersApi = {
  createUser: async (dto: CreateUserRequest): Promise<UserDto> => {
    const response = await api.post<UserDto>('/users', dto);
    return response.data;
  },

  getAllUsers: async (): Promise<UserDto[]> => {
    const response = await api.get<UserDto[]>('/users');
    return response.data;
  },

  getUserById: async (id: string): Promise<UserDto> => {
    const response = await api.get<UserDto>(`/users/${id}`);
    return response.data;
  },

  getMe: async (): Promise<UserDto> => {
    const response = await api.get<UserDto>('/users/me');
    return response.data;
  },

  getUsersByDepartment: async (departmentId: string): Promise<UserDto[]> => {
    const response = await api.get<UserDto[]>(
      `/users/department/${departmentId}`
    );
    return response.data;
  },

  getUsersByRole: async (role: Role): Promise<UserDto[]> => {
    const response = await api.get<UserDto[]>(
      `/users/role/${role}`
    );
    return response.data;
  },

  updateUser: async (
    id: string,
    dto: UpdateUserRequest
  ): Promise<UserDto> => {
    const response = await api.patch<UserDto>(
      `/users/${id}`,
      dto
    );
    return response.data;
  },

  deactivateUser: async (id: string): Promise<void> => {
    await api.delete(`/users/${id}`);
  },
};