import { apiFetch } from './client.js';

export function simulateEvent(data) {
  return apiFetch('/api/events/simulate', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}
