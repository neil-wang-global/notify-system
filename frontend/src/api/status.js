import { apiFetch } from './client.js';

export function getStatus() {
  return apiFetch('/api/status');
}
