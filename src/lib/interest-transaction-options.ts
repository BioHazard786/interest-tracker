import { getInterestProjection } from "@/server/projection-action"
import { infiniteQueryOptions } from "@tanstack/react-query"

export const interestTransactionOptions = (sort: "asc" | "desc" = "desc") =>
  infiniteQueryOptions({
    queryKey: ["interest-transaction", sort],
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      getInterestProjection({ cursor: pageParam, sortDirection: sort }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage => lastPage.nextCursor,
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
