"use client";

import { IconBrandGoogleFilled, IconMoodWrrr } from "@tabler/icons-react";
import { useTransition } from "react";
import { Button } from "@/components/ui/button";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import { authClient } from "@/lib/auth-client";

export function SignIn() {
  const [isPending, startTransition] = useTransition();

  const handleSignIn = () => {
    startTransition(async () => {
      await authClient.signIn.social({ provider: "google" });
    });
  };
  return (
    <div className="flex h-svh w-svw items-center justify-center">
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <IconMoodWrrr />
          </EmptyMedia>
          <EmptyTitle>Sign In Required</EmptyTitle>
          <EmptyDescription>
            You haven&apos;t signed in yet. Please sign in to start using the
            app.
          </EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Button disabled={isPending} onClick={handleSignIn} size="sm">
            {isPending ? (
              <>
                <Spinner />
                Signing In
              </>
            ) : (
              <>
                <IconBrandGoogleFilled />
                Sign In
              </>
            )}
          </Button>
        </EmptyContent>
      </Empty>
    </div>
  );
}
