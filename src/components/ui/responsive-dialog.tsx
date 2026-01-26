"use client"

import * as React from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "@/components/ui/drawer"
import { useIsMobile } from "@/hooks/use-mobile"

// Context to share isMobile state across all child components
const ResponsiveDialogContext = React.createContext<boolean>(false)

function useResponsiveDialogContext() {
  return React.useContext(ResponsiveDialogContext)
}

type ResponsiveDialogProps = {
  children?: React.ReactNode
  open?: boolean
  onOpenChange?: (open: boolean) => void
}

export function ResponsiveDialog({ children, ...props }: ResponsiveDialogProps) {
  const isMobile = useIsMobile()

  if (isMobile) {
    return (
      <ResponsiveDialogContext.Provider value={true}>
        <Drawer {...props}>{children}</Drawer>
      </ResponsiveDialogContext.Provider>
    )
  }

  return (
    <ResponsiveDialogContext.Provider value={false}>
      <Dialog {...props}>{children}</Dialog>
    </ResponsiveDialogContext.Provider>
  )
}

export function ResponsiveDialogTrigger({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DrawerTrigger>) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerTrigger className={className} {...props}>
        {children}
      </DrawerTrigger>
    )
  }

  return (
    <DialogTrigger className={className} render={<Button />} {...props}>
      {children}
    </DialogTrigger>
  )
}

export function ResponsiveDialogClose({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DrawerClose>) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerClose className={className} {...props}>
        {children}
      </DrawerClose>
    )
  }

  // Base UI DialogClose uses render prop, so we wrap children in a Button
  return (
    <DialogClose className={className} render={<Button variant="outline">{children}</Button>} />
  )
}

type ResponsiveDialogContentProps = React.ComponentProps<typeof DrawerContent> & {
  showCloseButton?: boolean
  disableCloseButton?: boolean
}

export function ResponsiveDialogContent({
  className,
  children,
  showCloseButton,
  disableCloseButton,
  ...props
}: ResponsiveDialogContentProps) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerContent className={className} {...props}>
        {children}
      </DrawerContent>
    )
  }

  return (
    <DialogContent
      className={className}
      showCloseButton={showCloseButton}
      disableCloseButton={disableCloseButton}
    >
      {children}
    </DialogContent>
  )
}

export function ResponsiveDialogDescription({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DrawerDescription>) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerDescription className={className} {...props}>
        {children}
      </DrawerDescription>
    )
  }

  return (
    <DialogDescription className={className} {...props}>
      {children}
    </DialogDescription>
  )
}

export function ResponsiveDialogHeader({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DialogHeader>) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerHeader className={className} {...props}>
        {children}
      </DrawerHeader>
    )
  }

  return (
    <DialogHeader className={className} {...props}>
      {children}
    </DialogHeader>
  )
}

export function ResponsiveDialogTitle({
  className,
  children,
  ...props
}: React.ComponentProps<typeof DrawerTitle>) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerTitle className={className} {...props}>
        {children}
      </DrawerTitle>
    )
  }

  return (
    <DialogTitle className={className} {...props}>
      {children}
    </DialogTitle>
  )
}

type ResponsiveDialogFooterProps = React.ComponentProps<typeof DrawerFooter> & {
  showCloseButton?: boolean
}

export function ResponsiveDialogFooter({
  className,
  children,
  showCloseButton,
  ...props
}: ResponsiveDialogFooterProps) {
  const isMobile = useResponsiveDialogContext()

  if (isMobile) {
    return (
      <DrawerFooter className={className} {...props}>
        {children}
      </DrawerFooter>
    )
  }

  return (
    <DialogFooter className={className} showCloseButton={showCloseButton} {...props}>
      {children}
    </DialogFooter>
  )
}
