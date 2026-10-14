export interface RoleSummary {
  id: number;
  code: string;
  name: string;
}

export interface PermissionItem {
  id: number;
  code: string;
  module: string;
  name: string;
  description?: string | null;
}

export interface PermissionGroup {
  module: string;
  label: string;
  permissions: PermissionItem[];
}

export interface CurrentUser {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  lastLoginAt?: string | null;
  mustChangePassword: boolean;
  roles: RoleSummary[];
  permissions: string[];
}

export interface LoginResult {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: CurrentUser;
}

export interface UserRecord {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  lastLoginAt?: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  mustChangePassword: boolean;
  roles: RoleSummary[];
}

export interface RoleRecord {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  systemRole: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
  permissions: PermissionItem[];
}

export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserWrite {
  fullName: string;
  email: string;
  phone: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  roleIds: number[];
  version?: number;
  password?: string;
  username?: string;
}
