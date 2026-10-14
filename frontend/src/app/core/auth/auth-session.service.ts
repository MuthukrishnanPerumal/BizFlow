import { Injectable, computed, inject, signal } from '@angular/core';

import { SessionUser } from '../models/session.model';
import { TokenStorage } from './token-storage.service';

interface TokenClaims {
  sub?: unknown;
  exp?: unknown;
  roles?: unknown;
  permissions?: unknown;
}

@Injectable({ providedIn: 'root' })
export class AuthSession {
  private readonly storage = inject(TokenStorage);
  private readonly token = signal<string | null>(null);
  private readonly profile = signal<SessionUser | null>(null);

  readonly user = computed(() => this.profile() ?? this.decodeUser(this.token()));
  readonly isAuthenticated = computed(() => this.decodeUser(this.token()) !== null);

  constructor() {
    const stored = this.storage.read();
    if (this.decodeUser(stored)) {
      this.token.set(stored);
      return;
    }
    if (stored) {
      this.storage.clear();
    }
  }

  establish(accessToken: string, profile: SessionUser): void {
    if (!this.decodeUser(accessToken)) {
      throw new Error('Access token is not valid');
    }
    this.storage.write(accessToken);
    this.token.set(accessToken);
    this.profile.set(profile);
  }

  replaceProfile(profile: SessionUser): void {
    this.profile.set(profile);
  }

  clear(): void {
    this.storage.clear();
    this.token.set(null);
    this.profile.set(null);
  }

  accessToken(): string | null {
    return this.isAuthenticated() ? this.token() : null;
  }

  hasPermission(permission: string): boolean {
    const current = this.user();
    if (permission === 'AUTHENTICATED') {
      return current !== null;
    }
    if (!current) {
      return false;
    }
    return current.permissions.includes(permission);
  }

  private decodeUser(token: string | null): SessionUser | null {
    const claims = this.claims(token);
    if (!claims || typeof claims.sub !== 'string' || claims.sub.trim().length === 0) {
      return null;
    }
    if (typeof claims.exp === 'number' && claims.exp * 1000 <= Date.now()) {
      return null;
    }
    return {
      id: null,
      username: claims.sub,
      fullName: claims.sub,
      email: '',
      phone: null,
      status: 'ACTIVE',
      roles: this.strings(claims.roles),
      permissions: this.strings(claims.permissions),
      lastLoginAt: null,
      mustChangePassword: false,
    };
  }

  private claims(token: string | null): TokenClaims | null {
    if (!token) {
      return null;
    }
    const parts = token.split('.');
    if (parts.length !== 3 || !parts[1]) {
      return null;
    }
    try {
      return JSON.parse(this.decode(parts[1])) as TokenClaims;
    } catch {
      return null;
    }
  }

  private strings(value: unknown): string[] {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
  }

  private decode(segment: string): string {
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    return atob(padded);
  }
}
