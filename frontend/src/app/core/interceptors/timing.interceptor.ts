import { HttpInterceptorFn } from '@angular/common/http';
import { tap } from 'rxjs';

import { environment } from '../../../environments/environment';

const SLOW_MS = 1000;

/**
 * Logs API timings in non-production builds. Never logs bodies, tokens, or PII.
 */
export const timingInterceptor: HttpInterceptorFn = (request, next) => {
  if (environment.production || !request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }
  const started = performance.now();
  return next(request).pipe(
    tap({
      next: () => logTiming(request.method, request.url, started, 'ok'),
      error: () => logTiming(request.method, request.url, started, 'error'),
    }),
  );
};

function logTiming(method: string, url: string, started: number, outcome: 'ok' | 'error'): void {
  const durationMs = Math.round(performance.now() - started);
  const path = url.startsWith(environment.apiUrl) ? url.slice(environment.apiUrl.length) : url;
  if (durationMs >= SLOW_MS) {
    console.warn(`[api-slow] ${method} ${path} ${durationMs}ms (${outcome})`);
  } else {
    console.debug(`[api] ${method} ${path} ${durationMs}ms (${outcome})`);
  }
}
