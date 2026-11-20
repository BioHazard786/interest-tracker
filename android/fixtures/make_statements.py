"""Generates SYNTHETIC bank statements (fake accounts, fake amounts) for parser tests.

Needs: pip install openpyxl reportlab
Run:   python3 make_statements.py   (writes into ./statements/)

These mimic the column layout the web parsers in src/lib/banking/parsers expect. They are not real
statements, so they prove the Kotlin parsers match the TypeScript ones on these shapes only.
"""

import datetime as dt
import os

import openpyxl
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

OUT = os.path.join(os.path.dirname(__file__), "statements")
os.makedirs(OUT, exist_ok=True)

# --- PNB (csv) -------------------------------------------------------------------------------
# Quirks on purpose: a balance with a thousands comma (JS parseFloat stops at the comma), a debit
# row that mentions interest, a non-interest row, and preamble lines before the header.
pnb = '''Account Statement for 1234567890123456
Customer: TEST USER
Period: 01-01-2025 to 30-06-2025

Txn No.,Txn Date,Description,Branch Name,Cheque Number,Dr Amount,Cr Amount,Balance,
101,31-03-2025,"1234567890123456:Int.Pd:01-01-2025 to 31-03-2025",TEST BRANCH,,,812.50,"1,234.50 Cr.",
102,05-04-2025,UPI/CR/501234567890/TEST PAYEE,TEST BRANCH,,,500.00,1734.50 Cr.,
103,30-06-2025,"1234567890123456:Int.Pd:01-04-2025 to 30-06-2025",TEST BRANCH,,,0.10,1734.60 Cr.,
104,01-07-2025,Interest recovery adjustment,TEST BRANCH,,5.25,,1729.35 Cr.,
'''
open(os.path.join(OUT, "pnb.csv"), "w", newline="").write(pnb)

# --- SBI (xlsx) ------------------------------------------------------------------------------
# One real date cell, one dd-mm-yyyy string, one dd/mm/yyyy string; "INTERES\nT CREDIT" wraps.
wb = openpyxl.Workbook()
ws = wb.active
ws.append(["State Bank of India"])
ws.append(["Account Statement"])
ws.append([])
ws.append(["Date", "Details", "Ref No./Cheque No.", "Debit", "Credit", "Balance"])
c = ws.cell(row=5, column=1, value=dt.datetime(2025, 3, 31))
c.number_format = "dd/mm/yyyy"
ws.cell(row=5, column=2, value="CREDIT INTERES\nT CREDIT-SBINT")
ws.cell(row=5, column=4, value=None)
ws.cell(row=5, column=5, value=1234.5)
ws.cell(row=5, column=6, value=22345.75)
ws.append(["30-06-2025", "INTERES T CREDIT SBINT", None, None, "1,100.25", "23,446.00"])
ws.append(["15/07/2025", "UPI/DR/PAYEE", None, "250.00", None, "23196.00"])
ws.append(["30-09-2025", "BY CREDIT INTERES T CREDIT", None, None, 99, 23295])
wb.save(os.path.join(OUT, "sbi.xlsx"))

# --- IDFC (xlsx) -----------------------------------------------------------------------------
wb = openpyxl.Workbook()
ws = wb.active
ws.append(["IDFC FIRST Bank"])
ws.append(["Statement of Account"])
ws.append([])
ws.append(["Transaction Date", "Value Date", "Particulars", "Cheque No.", "Debit", "Credit", "Balance"])
c = ws.cell(row=5, column=1, value=dt.datetime(2025, 8, 31))
c.number_format = "dd-mmm-yyyy"
ws.cell(row=5, column=2, value=dt.datetime(2025, 8, 31)).number_format = "dd-mmm-yyyy"
ws.cell(row=5, column=3, value="MONTHLY SAVINGS INTEREST CREDIT")
ws.cell(row=5, column=6, value=49.0)
ws.cell(row=5, column=7, value=41918.2)
ws.append(["30-Sep-2025", "30-Sep-2025", "Monthly Savings Interest Credit", None, None, "51.5", "41969.70"])
ws.append(["05-Oct-2025", "05-Oct-2025", "UPI/PAYEE/ref", None, "300", None, "41669.70"])
wb.save(os.path.join(OUT, "idfc.xlsx"))

# --- Kotak (pdf) -----------------------------------------------------------------------------
def draw_rows(path, title, rows, columns, font="Helvetica", size=9, extra=None):
    c = canvas.Canvas(path, pagesize=A4)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(40, 800, title)
    c.setFont(font, size)
    y = 760
    for row in rows:
        for text, x in zip(row, columns):
            c.drawString(x, y, text)
        y -= 34
    if extra:
        extra(c)
    c.save()

draw_rows(
    os.path.join(OUT, "kotak.pdf"),
    "Kotak Mahindra Bank - Statement",
    [
        ["#", "Date", "Narration", "Amount", "Balance"],
        ["130", "28 Feb 2025", "UPI-TEST PAYEE-ref", "500.00", "2,692.39"],
        ["131", "31 Mar 2025", "Int.Pd:1234567890:01-01-2025 to 31-03-2025", "12.00", "2,704.39"],
        ["132", "30 Jun 2025", "Int.Pd:1234567890:01-04-2025 to 30-06-2025", "1,012.34", "3,716.73"],
    ],
    [40, 70, 150, 430, 500],
)

# --- IDFC (pdf): description on one line, amounts on the next ---------------------------------
def idfc_pdf(path):
    c = canvas.Canvas(path, pagesize=A4)
    c.setFont("Helvetica-Bold", 22)
    c.drawString(40, 810, "IDFC FIRST Bank")
    c.setFont("Helvetica-Bold", 16)
    c.drawString(40, 785, "Statement of Account")
    y = 750
    for date, amount, balance in [("31-Aug-2025", "49.00", "41,918.20"), ("30-Sep-2025", "51.50", "41,969.70")]:
        c.setFont("Helvetica", 9)
        c.drawString(40, y, date)
        c.drawString(110, y, date)
        c.drawString(180, y, "MONTHLY SAVINGS INTEREST CREDIT")
        y -= 14
        c.setFont("Helvetica-Bold", 11)  # third-largest font on the page: pdf2md turns it into "###"
        c.drawString(180, y, amount)
        c.drawString(260, y, balance)
        y -= 28
    c.save()

idfc_pdf(os.path.join(OUT, "idfc.pdf"))
print("wrote", sorted(os.listdir(OUT)))
