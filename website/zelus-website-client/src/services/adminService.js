import { apiFetch } from './api.js';

export const fetchAdminUsers     = () => apiFetch('/admin/users');
export const fetchAdminDonations = () => apiFetch('/admin/donations');
