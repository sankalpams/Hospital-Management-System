import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * List state (search, filters, sort, page) lives in the URL so lists are bookmarkable,
 * survive reloads, and the browser Back button restores the previous view.
 */
export function useListParams(defaults = {}) {
  const [searchParams, setSearchParams] = useSearchParams();

  const params = useMemo(() => {
    const out = { page: 0, size: 20, ...defaults };
    searchParams.forEach((v, k) => { out[k] = k === 'page' || k === 'size' ? Number(v) : v; });
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const update = useCallback((changes, { resetPage = true } = {}) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      Object.entries(changes).forEach(([k, v]) => {
        if (v === undefined || v === null || v === '') next.delete(k);
        else next.set(k, String(v));
      });
      if (resetPage && !('page' in changes)) next.delete('page');
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  return [params, update];
}
