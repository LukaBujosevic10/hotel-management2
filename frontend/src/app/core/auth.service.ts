import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginResponse {
  token: string;
  type: string;
  username: string;
  role: 'MANAGER' | 'RECEPTIONIST';
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private tokenKey = 'hotel_token';

  /** Roles are read back from the token, so a page refresh keeps them. */
  private rolesSignal = signal<string[]>(this.readRoles(this.token));
  private usernameSignal = signal<string>(this.readUsername(this.token));

  isLoggedIn = signal<boolean>(!!localStorage.getItem(this.tokenKey));

  roles = this.rolesSignal.asReadonly();
  username = this.usernameSignal.asReadonly();

  isManager = computed(() => this.rolesSignal().includes('ROLE_MANAGER'));
  isReceptionist = computed(() => this.rolesSignal().includes('ROLE_RECEPTIONIST'));

  /** Human readable role, for the header. */
  roleLabel = computed(() => {
    if (this.isManager()) return 'Manager';
    if (this.isReceptionist()) return 'Receptionist';
    return this.isLoggedIn() ? 'User' : '';
  });

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, { username, password })
      .pipe(tap(res => {
        localStorage.setItem(this.tokenKey, res.token);
        this.isLoggedIn.set(true);
        this.rolesSignal.set(this.readRoles(res.token));
        this.usernameSignal.set(this.readUsername(res.token) || res.username);
      }));
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.isLoggedIn.set(false);
    this.rolesSignal.set([]);
    this.usernameSignal.set('');
  }

  get token(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  // ------------------------------------------------------------- JWT parsing

  /** Decodes the JWT payload. Returns null for anything malformed. */
  private decodePayload(token: string | null): Record<string, any> | null {
    if (!token) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
      return JSON.parse(atob(padded));
    } catch {
      return null;
    }
  }

  private readRoles(token: string | null): string[] {
    const payload = this.decodePayload(token);
    const roles = payload?.['roles'];
    return Array.isArray(roles) ? roles : [];
  }

  private readUsername(token: string | null): string {
    return this.decodePayload(token)?.['sub'] ?? '';
  }
}
