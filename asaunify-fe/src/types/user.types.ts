import type { Role } from './enums';

export interface UserDto {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  departmentId: string | null;
  departmentName: string | null;
  isActive: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  fullName: string;
  email: string;
  password: string;
  role: Role;
  departmentId?: string;
}

export interface UpdateUserRequest {
  fullName?: string;
  email?: string;
  role?: Role;
  departmentId?: string;
  isActive?: boolean;
}

export interface Department {
  id: string;
  name: string;
  headUserId: string | null;
}

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  fullName: string;
  email: string;
  role: Role;
  departmentName: string | null;
}