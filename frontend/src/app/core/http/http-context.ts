import { HttpContextToken } from '@angular/common/http';

export const SKIP_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);
export const SKIP_LOADING_INDICATOR = new HttpContextToken<boolean>(() => false);
export const SKIP_AUTH_LOGOUT = new HttpContextToken<boolean>(() => false);
