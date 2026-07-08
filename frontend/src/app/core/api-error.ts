import { HttpErrorResponse } from '@angular/common/http';

/** Error body returned by every backend service (GlobalExceptionHandler). */
export interface ApiError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

/** checkInDate -> "Check in date" */
function humanizeField(field: string): string {
  const spaced = field.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

/**
 * Turns any failed HTTP call into a sentence a user can act on.
 * Prefers the backend's own message, falls back to per-field details,
 * and explains transport failures instead of showing "Http failure response".
 */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }

  // The browser could not reach the gateway at all.
  if (err.status === 0) {
    return 'Cannot reach the server. Check that the API gateway is running and try again.';
  }

  const body: ApiError | string | null = err.error;

  if (typeof body === 'string' && body.trim()) {
    return body;
  }

  if (body && typeof body === 'object') {
    if (body.message && body.message.trim()) {
      return body.message;
    }
    if (body.fieldErrors && Object.keys(body.fieldErrors).length) {
      return Object.entries(body.fieldErrors)
        .map(([field, msg]) => `${humanizeField(field)} ${msg.charAt(0).toLowerCase()}${msg.slice(1)}`)
        .join('. ') + '.';
    }
  }

  switch (err.status) {
    case 401: return 'Your session has expired. Please sign in again.';
    case 403: return 'You are not allowed to perform this action.';
    case 404: return 'The requested item no longer exists.';
    case 409: return 'This action conflicts with existing data.';
    case 503: return 'A service is temporarily unavailable. Please try again in a moment.';
    default: return fallback;
  }
}

/** Per-field messages, for showing errors next to the inputs that caused them. */
export function fieldErrors(err: unknown): Record<string, string> {
  if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object') {
    return (err.error as ApiError).fieldErrors ?? {};
  }
  return {};
}
