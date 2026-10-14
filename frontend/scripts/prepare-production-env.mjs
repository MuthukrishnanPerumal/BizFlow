/**
 * Writes environment.production.ts from API_URL before a production build.
 * - Unset API_URL → same-origin /api/v1 (nginx-style deploy)
 * - Set API_URL → absolute backend origin or full .../api/v1 URL (Vercel + Render)
 * Rejects localhost so production bundles never call a local API.
 */
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outFile = join(root, 'src', 'environments', 'environment.production.ts');

const raw = (process.env.API_URL || '').trim().replace(/\/+$/, '');

function resolveApiUrl(value) {
  if (!value) {
    return '/api/v1';
  }
  if (/^https?:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/i.test(value)) {
    console.error('API_URL must not point to localhost in a production build.');
    process.exit(1);
  }
  if (!/^https:\/\//i.test(value)) {
    console.error('API_URL must be an https:// origin (or leave unset for same-origin /api/v1).');
    process.exit(1);
  }
  if (/\/api\/v1$/i.test(value)) {
    return value;
  }
  return `${value}/api/v1`;
}

const apiUrl = resolveApiUrl(raw);

const contents = `export const environment = {
  production: true,
  apiUrl: ${JSON.stringify(apiUrl)},
  authRequired: true,
};
`;

writeFileSync(outFile, contents, 'utf8');
console.log(`Prepared production apiUrl=${apiUrl}`);
