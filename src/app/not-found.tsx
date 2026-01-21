import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty"
import { IconMoodPuzzled } from "@tabler/icons-react"
import Link from "next/link"

function NotFound() {
  return (
    <Empty className="min-h-screen">
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <IconMoodPuzzled />
        </EmptyMedia>
        <EmptyTitle>404 - Not Found</EmptyTitle>
        <EmptyDescription>The page you&apos;re looking for doesn&apos;t exist.</EmptyDescription>
      </EmptyHeader>
      <EmptyContent>
        <Button
          size="sm"
          variant="outline"
          render={
            <Link href="/" replace>
              Go to Home
            </Link>
          }
          nativeButton={false}
        />
      </EmptyContent>
    </Empty>
  )
}

export default NotFound
