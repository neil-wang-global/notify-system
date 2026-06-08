export async function apiFetch(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });
  if (!response.ok) {
    const contentType = response.headers.get('content-type');
    const error = contentType && contentType.includes('application/json')
      ? await response.json().catch(() => ({}))
      : {};
    throw new Error(error.message || `HTTP ${response.status}`);
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return null;
  }
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json();
  }
  return null;
}
