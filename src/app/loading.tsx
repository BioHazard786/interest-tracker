import { IconLoader3 } from "@tabler/icons-react"

export default function Loading() {
  return (
    <div className="flex h-svh items-center justify-center">
      <IconLoader3 stroke={1} className="size-8 animate-spin" />
    </div>
  )
}
