import { apiFetch } from "@/lib/api"
import type { ProjectionPage, StatusFilter } from "@/server/core/projection"
import { infiniteQueryOptions } from "@tanstack/react-query"

export const interestTransactionOptions = (
  sort: "asc" | "desc" = "desc",
  statusFilter?: StatusFilter,
) =>
  infiniteQueryOptions({
    queryKey: ["interest-transaction", sort, statusFilter],
    queryFn: ({ pageParam }: { pageParam: string | undefined }) => {
      const params = new URLSearchParams({ sortDirection: sort })
      if (pageParam) params.set("cursor", pageParam)
      statusFilter?.forEach(s => params.append("statusFilter", s))
      return apiFetch<ProjectionPage>(`/projection?${params}`)
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage => lastPage.nextCursor,
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
