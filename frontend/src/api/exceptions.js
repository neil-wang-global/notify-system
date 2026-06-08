import { apiFetch } from './client.js';

export function listUserOperationExceptions() {
  return apiFetch('/api/exceptions/user-operations');
}

export function listNotificationExceptions() {
  return apiFetch('/api/exceptions/notifications');
}
