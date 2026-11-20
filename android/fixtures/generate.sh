#!/usr/bin/env bash
# Regenerates expected.json from the real TypeScript parsers (V8, Asia/Kolkata like the web app's users).
# Needs bun + node. Run from anywhere.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$here/../.."
(cd "$root" && bun run android/fixtures/pdf-to-md.ts)
bundle="$(mktemp -d)/golden.mjs"
(cd "$root" && bun build android/fixtures/generate-golden.ts --target=node \
  --tsconfig-override=android/fixtures/tsconfig.golden.json --outfile="$bundle" >/dev/null)
(cd "$here" && TZ=Asia/Kolkata node "$bundle")
