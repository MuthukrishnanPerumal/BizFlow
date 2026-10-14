export interface SessionUser {
  id: number | null;
  username: string;
  fullName: string;
  email: string;
  phone: string | null;
  status: string;
  roles: string[];
  permissions: string[];
  lastLoginAt: string | null;
  mustChangePassword: boolean;
}
