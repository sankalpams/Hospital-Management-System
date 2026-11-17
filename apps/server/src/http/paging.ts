import { z } from 'zod';

export const MAX_PAGE_SIZE = 200;
export const DEFAULT_PAGE_SIZE = 50;

export const pagingSchema = z.object({
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(MAX_PAGE_SIZE).default(DEFAULT_PAGE_SIZE),
});

export interface Paged<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

/** Parses page/pageSize from a query object; throws a ZodError (-> 400) when invalid. */
export function parsePaging(query: unknown): { page: number; pageSize: number; skip: number; take: number } {
  const { page, pageSize } = pagingSchema.parse(query ?? {});
  return { page, pageSize, skip: (page - 1) * pageSize, take: pageSize };
}
