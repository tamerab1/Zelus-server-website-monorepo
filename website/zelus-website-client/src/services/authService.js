import { apiFetch } from './api.js';

export const login = (credentials) =>
  apiFetch('/login', { method: 'POST', body: JSON.stringify(credentials) });

export const register = (userData) =>
  apiFetch('/register', { method: 'POST', body: JSON.stringify(userData) });
