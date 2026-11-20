// Runs the REAL pdf2md (what the web app uses) over the real-*.pdf fixtures and saves the markdown next to them.
// generate-golden.ts feeds that markdown to the web parsers (see pdf2md-stub.ts). Run by generate.sh.
import pdf2md from "@opendocsg/pdf2md"
import { readFileSync, writeFileSync } from "node:fs"
import { join } from "node:path"

const dir = join(import.meta.dir, "statements")
for (const name of ["real-kotak", "real-idfc"]) {
  const markdown = await pdf2md(readFileSync(join(dir, `${name}.pdf`)))
  writeFileSync(join(dir, `${name}.md.txt`), markdown)
}
