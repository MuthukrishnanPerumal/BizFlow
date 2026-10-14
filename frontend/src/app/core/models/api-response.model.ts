export interface FieldErrorDetail {
  field?: string | null;
  code?: string | null;
  message: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
  errors?: FieldErrorDetail[];
  timestamp: string;
}
