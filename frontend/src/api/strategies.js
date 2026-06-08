import { apiFetch } from './client.js';

export function listStrategies() {
  return apiFetch('/api/strategies');
}

export function getStrategy(id) {
  return apiFetch(`/api/strategies/${encodeURIComponent(id)}`);
}

export function saveStrategy(data) {
  return apiFetch('/api/strategies', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updateStrategy(id, data) {
  return apiFetch(`/api/strategies/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteStrategy(id) {
  return apiFetch(`/api/strategies/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
