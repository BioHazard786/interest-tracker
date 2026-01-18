import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemMedia,
  ItemTitle,
} from "@/components/ui/item"

interface StatsCardProps {
  description: string
  value: number
  icon: React.ReactNode
  action?: React.ReactNode
}

export function StatsCard({ description, value, icon, action }: StatsCardProps) {
  return (
    <Item variant="outline">
      <ItemMedia variant="icon">{icon}</ItemMedia>
      <ItemContent>
        <ItemTitle>₹{value.toLocaleString()}</ItemTitle>
        <ItemDescription>{description}</ItemDescription>
      </ItemContent>
      <ItemActions>{action}</ItemActions>
    </Item>
  )
}
