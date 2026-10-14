import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';

import { SKIP_LOADING_INDICATOR } from '../http/http-context';
import { LoadingService } from '../services/loading.service';

export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.context.get(SKIP_LOADING_INDICATOR)) {
    return next(request);
  }
  const loading = inject(LoadingService);
  loading.begin();
  return next(request).pipe(finalize(() => loading.end()));
};
