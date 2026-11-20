"""Turns REAL bank statements into shareable test fixtures (real-*.{csv,xlsx,pdf} in ./statements/).

Needs: pip install pymupdf pikepdf openpyxl
Run:   python3 anonymize_real.py [DIR_WITH_REAL_STATEMENTS]    (default ../../sample_files)
Reads: pnb.csv, kotak_bank.csv, kotak_bank.pdf, idfc_first_bank.xlsx, idfc_first_bank.pdf (any missing one is skipped)

What changes: every word and number that is not a known label is replaced by random characters of the same
shape (names, addresses, accounts, merchants, UPI ids ...), images and PDF metadata are dropped, and long
statements are cut down to the head, the interest rows (with some rows around them) and the tail.
What stays: the layout, the real transaction dates, and the labels the parsers read.
Interest amounts and balances are changed by the same formula in every format, so the Kotak CSV and PDF
(and the IDFC xlsx and PDF) still describe the same transactions and hash alike.
Output is deterministic. Finish with a look at the files and `generate.sh` to refresh expected.json.
"""

import csv
import io
import os
import random
import re
import sys

import openpyxl
import pikepdf
import pymupdf

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "sample_files"))
OUT = os.path.join(HERE, "statements")
SALT = "interest-tracker-fixtures"
LOCKED_PASSWORD = "test-1234"

# Words that survive: table headings, bank vocabulary, transaction-type prefixes. Everything else is scrambled.
LABELS = set(
    """account statement of date description balance withdrawal deposit dr cr debit credit particulars cheque chq
    ref no value transaction transactions opening closing total number branch name address city pin ifsc micr code
    customer details period to from txn amount id savings regular type currency inr indian rupee status active
    nominee registered nomination email phone ckyc communication generated on page int pd interest monthly upi neft
    imps nach pcd atm mb kb wb rtgs ecs sweep transfer chrg fee card annual important message safety tips end the
    and for bank first idfc kotak mahindra punjab national of statement a an sent using payment cashback refund
    debit credit by in is mr ms mrs""".split()
)

DATE = re.compile(
    r"(?<!\d)(?:\d{1,2}[ -]\w{3}[ -]\d{4}|\d{1,2}[-/]\d{2}[-/]\d{4}|\d{4}-\d{2}-\d{2}|\d{1,2}:\d{2})(?!\d)"
)
TOKEN = re.compile(r"\d[\d,]*(?:\.\d+)?|[A-Za-z]+")

# value -> replacement, filled from the real interest rows by collect_interest()
CHANGED: dict[float, float] = {}


def new_amount(v: float) -> float:
    return float(round(v * 1.25 + 2))


def new_balance(v: float) -> float:
    return round(v * 1.1 + 13.37, 2)


def fmt_like(original: str, value: float) -> str:
    decimals = len(original.split(".")[1]) if "." in original else 0
    text = f"{value:,.{decimals}f}" if "," in original else f"{value:.{decimals}f}"
    return text


def scramble(token: str) -> str:
    rng = random.Random(SALT + token)
    out = []
    for i, ch in enumerate(token):
        if ch.isdigit():
            lead = i == 0 and len(token) > 1 and token[1] in "0123456789,."
            out.append(rng.choice("123456789" if lead else "0123456789"))
        elif ch.isalpha():
            out.append(rng.choice("abcdefghijklmnopqrstuvwxyz").upper() if ch.isupper() else rng.choice("abcdefghijklmnopqrstuvwxyz"))
        else:
            out.append(ch)
    return "".join(out)


def anon_text(text: str) -> str:
    keep = [m.span() for m in DATE.finditer(text)]

    def sub(m: re.Match) -> str:
        if any(a < m.end() and m.start() < b for a, b in keep):
            return m.group(0)
        token = m.group(0)
        if token[0].isdigit():
            value = float(token.replace(",", ""))
            return fmt_like(token, CHANGED[value]) if value in CHANGED else scramble(token)
        return token if token.lower() in LABELS else scramble(token)

    return TOKEN.sub(sub, text)


def number(text: str) -> float | None:
    try:
        return float(text.replace(",", "").split()[0])
    except (ValueError, IndexError):
        return None


def note_interest(amount: str, balance: str) -> None:
    a, b = number(amount), number(balance)
    if a is not None:
        CHANGED[a] = new_amount(a)
    if b is not None:
        CHANGED[b] = new_balance(b)


def rows_to_keep(n: int, hits: list[int], head: int, around: int, tail: int) -> list[int]:
    keep = set(range(min(head, n))) | set(range(max(n - tail, 0), n))
    for h in hits:
        keep |= set(range(max(h - around, 0), min(h + around + 1, n)))
    return sorted(keep)


# ---------------------------------------------------------------------------------------------
# CSV
# ---------------------------------------------------------------------------------------------


def write_csv(name: str, rows: list[list[str]]) -> None:
    buf = io.StringIO()
    csv.writer(buf, lineterminator="\n").writerows(rows)
    with open(os.path.join(OUT, name), "w", newline="") as f:
        f.write(buf.getvalue())
    print("wrote", name, len(rows), "rows")


def anon_pnb(path: str) -> None:
    rows = list(csv.reader(open(path, newline="", encoding="utf-8-sig")))
    head = next(i for i, r in enumerate(rows) if "Txn No." in r and "Txn Date" in r)
    col = {c: i for i, c in enumerate(rows[head])}
    is_txn = lambda r: len(r) > col["Balance"] and DATE.fullmatch(r[col["Txn Date"]] or "") is not None
    end = next((i for i in range(head + 1, len(rows)) if not is_txn(rows[i])), len(rows))
    hits = [i for i in range(head + 1, end) if re.search(r"int\.pd|interest", rows[i][col["Description"]], re.I)]
    for i in hits:
        note_interest(rows[i][col["Cr Amount"]], rows[i][col["Balance"]])
    keep = [head + 1 + k for k in rows_to_keep(end - head - 1, [h - head - 1 for h in hits], 10, 3, 5)]
    out = [[anon_text(c) for c in r] for r in rows[:head]]
    out.append(rows[head])
    for i in keep:
        out.append([c if k == col["Txn Date"] else anon_text(c) for k, c in enumerate(rows[i])])
    out += rows[end:]  # the bank's footer notes, no personal data
    write_csv("real-pnb.csv", out)


def anon_kotak_csv(path: str) -> None:
    rows = list(csv.reader(open(path, newline="", encoding="utf-8-sig")))
    head = next(i for i, r in enumerate(rows) if "Description" in r and "Deposit (Cr.)" in r)
    n = len(rows)
    hits = [i for i in range(head + 1, n) if len(rows[i]) > 5 and re.search(r"Int\.Pd:", rows[i][2])]
    for i in hits:
        note_interest(rows[i][5], rows[i][6])
    # Page breaks inside the table are two/three-cell rows; keep a few to prove they are skipped.
    breaks = [i for i in range(head + 1, n) if len(rows[i]) in (2, 3)][:3]
    keep = sorted(set(rows_to_keep(n, hits, head + 40, 6, 12)) | {j for b in breaks for j in range(b - 2, b + 3) if j > head})
    out = [[anon_text(c) for c in r] for r in rows[:head]] + [rows[head]]
    for i in keep:
        if i > head:
            out.append([c if k == 0 and re.fullmatch(r"\d+|-", c) else anon_text(c) for k, c in enumerate(rows[i])])
    write_csv("real-kotak.csv", out)


# ---------------------------------------------------------------------------------------------
# XLSX
# ---------------------------------------------------------------------------------------------


def anon_idfc_xlsx(path: str) -> None:
    wb = openpyxl.load_workbook(path)
    ws = wb.worksheets[0]
    rows = list(ws.iter_rows(values_only=True))
    head = next(i for i, r in enumerate(rows) if r[0] == "Transaction Date")
    hits = [i for i in range(head + 1, len(rows)) if isinstance(rows[i][2], str) and "INTEREST" in rows[i][2].upper()]
    for i in hits:
        note_interest(str(rows[i][5]), str(rows[i][6]))
    keep = set(range(head + 1)) | set(rows_to_keep(len(rows), hits, head + 8, 4, 8))
    for i in range(len(rows), 0, -1):  # openpyxl rows are 1-based
        if i - 1 not in keep:
            ws.delete_rows(i)
    for row in ws.iter_rows():
        for c in row:
            if isinstance(c.value, str):
                c.value = anon_text(c.value)
            elif isinstance(c.value, (int, float)) and not isinstance(c.value, bool):
                v = float(c.value)
                c.value = CHANGED[v] if v in CHANGED else round(v * random.Random(SALT + repr(v)).uniform(0.4, 1.6), 2)
    wb.save(os.path.join(OUT, "real-idfc.xlsx"))
    print("wrote real-idfc.xlsx")


# ---------------------------------------------------------------------------------------------
# PDF
# ---------------------------------------------------------------------------------------------


def cmap(font) -> dict[int, str]:
    """glyph id -> text, from the font's ToUnicode map (the bank PDFs use 2-byte Identity-H fonts)."""
    if "/ToUnicode" not in font:
        return {}
    text = font.ToUnicode.read_bytes().decode("latin1")
    table: dict[int, str] = {}
    uni = lambda h: bytes.fromhex(h).decode("utf-16-be")
    for sec in re.findall(r"beginbfchar(.*?)endbfchar", text, re.S):
        for src, dst in re.findall(r"<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>", sec):
            table[int(src, 16)] = uni(dst)
    for sec in re.findall(r"beginbfrange(.*?)endbfrange", text, re.S):
        for lo, hi, dst in re.findall(r"<([0-9a-fA-F]+)>\s*<([0-9a-fA-F]+)>\s*(<[0-9a-fA-F]+>|\[[^\]]*\])", sec):
            if dst.startswith("["):
                for k, d in enumerate(re.findall(r"<([0-9a-fA-F]+)>", dst)):
                    table[int(lo, 16) + k] = uni(d)
            else:
                first = ord(uni(dst[1:-1]))
                for k in range(int(hi, 16) - int(lo, 16) + 1):
                    table[int(lo, 16) + k] = chr(first + k)
    return table


def reencode(text: str, table: dict[int, str]) -> bytes:
    """Back to glyph ids. A scrambled letter the font subset doesn't have becomes one it does (same kind)."""
    rev: dict[str, int] = {}
    for gid, ch in table.items():
        rev.setdefault(ch, gid)
    kinds = {"d": [c for c in rev if c.isdigit()], "u": [c for c in rev if c.isupper()], "l": [c for c in rev if c.islower()]}
    out = bytearray()
    for i, ch in enumerate(text):
        if ch not in rev:
            kind = "d" if ch.isdigit() else "u" if ch.isupper() else "l" if ch.islower() else None
            pool = kinds.get(kind) or [" "] if kind else [" "]
            ch = random.Random(SALT + text + str(i)).choice(pool) if pool and pool[0] in rev else " "
        out += rev[ch].to_bytes(2, "big")
    return bytes(out)


def anon_pdf(path: str, name: str, interest: re.Pattern) -> None:
    """Rewrites the text in place (same fonts, same order, same positions) so pdf2md and PDFBox see the real layout."""
    src = pymupdf.open(path)
    pages = sorted({0} | {i for i, p in enumerate(src) if interest.search(p.get_text())})
    pdf = pikepdf.open(path)
    for i in reversed(range(len(pdf.pages))):
        if i not in pages:
            del pdf.pages[i]
    for page in pdf.pages:
        fonts = {str(k): (v, cmap(v)) for k, v in page.Resources.Font.items()}
        table: dict[int, str] = {}
        current = None
        ops = []
        for ins in pikepdf.parse_content_stream(page):
            op = str(ins.operator)
            if op == "Do":  # logos, QR codes and the like
                continue
            if op == "Tf":
                current = fonts[str(ins.operands[0])]
            if op in ("Tj", "TJ") and current:
                table = current[1]
                if not table:  # a font we can't read could carry personal text: drop it rather than keep it
                    continue
                inner = ins.operands[0]
                strings = [bytes(inner)] if op == "Tj" else [bytes(x) for x in inner if isinstance(x, pikepdf.String)]
                text = "".join("".join(table.get(int.from_bytes(b[k : k + 2], "big"), "?") for k in range(0, len(b), 2)) for b in strings)
                encoded = pikepdf.String(reencode(anon_text(text), table))
                ins = pikepdf.ContentStreamInstruction([encoded] if op == "Tj" else [pikepdf.Array([encoded])], ins.operator)
            ops.append(ins)
        page.Contents = pdf.make_stream(pikepdf.unparse_content_stream(ops))
    for key in list(pdf.docinfo.keys()):
        del pdf.docinfo[key]
    if "/Metadata" in pdf.Root:
        del pdf.Root["/Metadata"]
    pdf.remove_unreferenced_resources()
    tmp = os.path.join(OUT, name + ".tmp")
    pdf.save(tmp)
    doc = pymupdf.open(tmp)
    doc.set_metadata({})
    doc.scrub()
    doc.save(os.path.join(OUT, name), garbage=4, deflate=True)
    if name == "real-kotak.pdf":  # the same statement behind a password, for the unlock flow (password: LOCKED_PASSWORD)
        doc.save(os.path.join(OUT, "real-kotak-locked.pdf"), garbage=4, deflate=True,
                 encryption=pymupdf.PDF_ENCRYPT_AES_128, user_pw=LOCKED_PASSWORD, owner_pw=LOCKED_PASSWORD + "-owner")
    doc.close()
    os.remove(tmp)
    print("wrote", name, "pages", pages)


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    src = lambda n: os.path.join(SRC, n)
    have = lambda n: os.path.exists(src(n))
    # Interest values must be collected from the spreadsheets/CSVs before any text is scrambled.
    if have("kotak_bank.csv"):
        anon_kotak_csv(src("kotak_bank.csv"))
    if have("idfc_first_bank.xlsx"):
        anon_idfc_xlsx(src("idfc_first_bank.xlsx"))
    if have("pnb.csv"):
        anon_pnb(src("pnb.csv"))
    if have("kotak_bank.pdf"):
        anon_pdf(src("kotak_bank.pdf"), "real-kotak.pdf", re.compile(r"Int\.Pd:"))
    if have("idfc_first_bank.pdf"):
        anon_pdf(src("idfc_first_bank.pdf"), "real-idfc.pdf", re.compile(r"MONTHLY SAVINGS INTEREST"))


main()
