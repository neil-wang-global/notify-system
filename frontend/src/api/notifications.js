import { apiFetch } from './client.js';

export function listNotifications() {
  return apiFetch('/api/notifications');
}
