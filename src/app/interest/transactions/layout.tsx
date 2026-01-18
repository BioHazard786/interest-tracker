import { Providers } from "@/components/providers"

export default function Layout({ children }: { children: React.ReactNode }) {
  return <Providers>{children}</Providers>
}
