import { ApiResponse } from '../models/api-response.model';

export function unwrapData<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data === undefined || response.data === null) {
    throw new Error(response.message || 'Request could not be completed');
  }
  return response.data;
}
