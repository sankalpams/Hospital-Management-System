import { useQuery } from '@tanstack/react-query';
import type { Settings } from '@hms/shared';
import { api } from './api';
import { useAuth } from '../auth/useAuth';

export type PublicSettings = Partial<Settings> & { hospitalName?: string };

/** GET /settings/public is open to any signed-in user, including those who must change their password. */
export function usePublicSettings() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['settings', 'public'],
    queryFn: () => api<PublicSettings>('/settings/public'),
    enabled: !!user,
    staleTime: 5 * 60_000,
  });
}
