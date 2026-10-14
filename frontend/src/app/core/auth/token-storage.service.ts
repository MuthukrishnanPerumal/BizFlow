import { Injectable } from '@angular/core';

import { STORAGE_KEYS } from '../constants/storage-keys';

@Injectable({ providedIn: 'root' })
export class TokenStorage {
  read(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEYS.accessToken);
    } catch {
      return null;
    }
  }

  write(token: string): void {
    sessionStorage.setItem(STORAGE_KEYS.accessToken, token);
  }

  clear(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEYS.accessToken);
    } catch {
      // Storage can be unavailable in locked-down browsers.
    }
  }
}
