import { getInterestProjection, type StatusFilter } from "@/server/projection-action"
import { infiniteQueryOptions } from "@tanstack/react-query"

export const interestTransactionOptions = (
  sort: "asc" | "desc" = "desc",
  statusFilter?: StatusFilter,
) =>
  infiniteQueryOptions({
    queryKey: ["interest-transaction", sort, statusFilter],
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      getInterestProjection({ cursor: pageParam, sortDirection: sort, statusFilter }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage => lastPage.nextCursor,
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
