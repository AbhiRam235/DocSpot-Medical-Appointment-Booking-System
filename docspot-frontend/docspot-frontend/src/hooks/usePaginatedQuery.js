import { useQuery, keepPreviousData } from "@tanstack/react-query";

/**
 * Wraps React Query around the backend's Spring Page<T> shape:
 * { content, totalElements, totalPages, number, size, first, last, empty }
 * `number` is the current page, 0-indexed.
 */
export function usePaginatedQuery({ queryKey, queryFn, page, size = 3, enabled = true }) {
  const query = useQuery({
    queryKey: [...queryKey, { page, size }],
    queryFn: () => queryFn({ page, size }),
    placeholderData: keepPreviousData,
    enabled,
  });

  return {
    ...query,
    content: query.data?.content ?? [],
    totalPages: query.data?.totalPages ?? 0,
    totalElements: query.data?.totalElements ?? 0,
    isFirst: query.data?.first ?? true,
    isLast: query.data?.last ?? true,
    isEmpty: query.data?.empty ?? true,
  };
}
