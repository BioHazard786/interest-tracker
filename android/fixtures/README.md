# Parser fixtures

The Kotlin statement parsers (`app/src/main/kotlin/.../parsing`) must produce the **same transaction hash**
as the web parsers (`src/lib/banking/parsers`), otherwise a statement uploaded from the phone and from the
web would be stored twice. These fixtures pin that.

- `statements/` – statements the tests read. Two kinds, and never a statement as the bank issued it:
  - `real-*` – **altered copies of real statements** (`anonymize_real.py`): same layout, fonts and dates, but
    every name, number, merchant and amount is scrambled and long files are cut down. `real-kotak.csv` and
    `real-kotak.pdf` are the same statement, as are `real-idfc.xlsx` and `real-idfc.pdf`, so the tests also prove
    the formats hash alike. `real-kotak-locked.pdf` is `real-kotak.pdf` behind the password `test-1234`.
  - the rest – **synthetic** statements (`make_statements.py`) built to hit odd cases on purpose.
  - `*.md.txt` – the markdown shape the web's `pdf2md` produces for the Kotak/IDFC PDFs.
  - `*.pdfbox.txt` – the lines Apache PDFBox extracts from `kotak.pdf` / `idfc.pdf` (what the phone sees).
- `expected.json` – what the **real TypeScript parsers** output for those files (hash input, hash, fields),
  recorded in `Asia/Kolkata` on V8.
- `make_statements.py` – regenerates the synthetic files (`pip install openpyxl reportlab`).
- `anonymize_real.py` – regenerates the `real-*` files from your own statements, by default from
  `../../sample_files` (gitignored; `pip install pymupdf pikepdf openpyxl`). Look at the output before committing.
- `generate.sh` – runs the real web `pdf2md` over the `real-*.pdf` files (`pdf-to-md.ts` -> `real-*.md.txt`), then
  regenerates `expected.json` (needs `bun` and `node`). Run it after either script above.

`GoldenParsersTest` compares each Kotlin parser with `expected.json`: `./gradlew :app:testDebugUnitTest`. The tests
run PDFBox on the JVM (the aar's glyph lists are put on the test classpath by `app/build.gradle.kts`), so the real
PDFs are parsed the way the phone parses them.

Why the timezone matters: SBI and IDFC hash `Date.toISOString()`. A spreadsheet date *cell* is UTC midnight,
but a date *string* becomes local midnight, so the same statement hashes differently in different timezones.
The phone uses its own timezone, like the browser does.
