import { apiFetch } from './api.js';

export const fetchHiscores = (sort = 'total_level', limit = 50, mode = null, difficulty = null) =>
  apiFetch(`/hiscores?sort=${sort}&limit=${limit}${mode ? `&mode=${mode}` : ''}${difficulty ? `&difficulty=${difficulty}` : ''}`);
