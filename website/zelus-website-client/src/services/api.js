const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';

// Thrown when the backend is unreachable (network error / server down)
export class ServerUnreachableError extends Error {
  constructor() {
    super('Unable to reach the server. Please check your connection and try again.');
    this.name = 'ServerUnreachableError';
  }
}

/**
 * Thin fetch wrapper — centralises base URL, JSON headers, and error handling.
 * Throws ServerUnreachableError on network failure, Error on non-2xx response.
 */
export const apiFetch = async (endpoint, options = {}) => {
  const { headers, ...rest } = options;
  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      headers: { 'Content-Type': 'application/json', ...headers },
      ...rest,
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.detail || 'Request failed.');
    return data;
  } catch (error) {
    // TypeError is thrown by fetch() when the network is unreachable
    if (error instanceof TypeError) throw new ServerUnreachableError();
    throw error;
  }
};
