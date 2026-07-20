import { apiFetch } from './api.js';

export const fetchHiscores = (sort = 'total_level', limit = 50) =>
  apiFetch(`/hiscores?sort=${sort}&limit=${limit}`);
