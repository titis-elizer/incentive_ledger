#!/usr/bin/env python3
"""Standard-library-only demo. Never deletes data. All money assertions use Decimal."""
import argparse
import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

PARTNER = os.getenv("PARTNER_URL", "http://127.0.0.1:8081").rstrip("/")
LEDGER = os.getenv("INCENTIVE_URL", "http://127.0.0.1:8082").rstrip("/")


def request(base, path, method="GET", body=None, expected=200):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    result = json.loads(payload) if payload else None
    if status != expected:
        raise RuntimeError(f"{method} {path}: expected {expected}, got {status}: {result}")
    return result


def equal(actual, expected, label):
    if Decimal(str(actual)) != Decimal(str(expected)):
        raise RuntimeError(f"{label}: expected {expected}, got {actual}")


def previous_month():
    jakarta = dt.timezone(dt.timedelta(hours=7))
    first = dt.datetime.now(jakarta).date().replace(day=1)
    return (first - dt.timedelta(days=1)).strftime("%Y-%m")


def health():
    for base in (PARTNER, LEDGER):
        if request(base, "/actuator/health")["status"] != "UP":
            raise RuntimeError(f"Not healthy: {base}")
        request(base, "/v3/api-docs")


def smoke():
    health()
    request(PARTNER, "/api/v1/partners?size=101", expected=400)
    request(LEDGER, "/api/v1/sales?size=0", expected=400)
    request(LEDGER, "/api/v1/reports/monthly?period=2026-13", expected=400)
    request(LEDGER, "/api/v1/settlements/" + str(uuid.uuid4()), expected=404)
    code = "SMK-" + uuid.uuid4().hex[:12].upper()
    partner = request(PARTNER, "/api/v1/partners", "POST", {"code": code, "name": "Smoke partner"}, 201)
    request(PARTNER, "/api/v1/partners/" + partner["id"] + "/status", "PATCH", {"status": "INACTIVE"})
    request(LEDGER, "/api/v1/sales", "POST", {
        "externalReference": code, "partnerId": partner["id"], "saleDate": previous_month() + "-15", "amount": 1
    }, 422)
    print("SMOKE PASS: health, OpenAPI, pagination, period, local 404, and real inter-service inactive validation")


def demo(period):
    health()
    try:
        parsed = dt.datetime.strptime(period, "%Y-%m").date()
        if parsed.strftime("%Y-%m") != period or period > previous_month():
            raise ValueError()
    except ValueError:
        raise RuntimeError("Choose a completed calendar month in YYYY-MM format") from None
    report_path = "/api/v1/reports/monthly?period=" + period
    before = request(LEDGER, report_path)
    if before["totalElements"]:
        raise RuntimeError("Demo requires an empty month; existing sales/settlement found. Use a fresh demo database or explicitly reset it yourself. Nothing was deleted.")
    prefix = "DEMO-" + uuid.uuid4().hex[:12].upper()
    print(f"period={period} prefix={prefix}")
    partners = {}
    for letter in "ABCD":
        partner = request(PARTNER, "/api/v1/partners", "POST", {
            "code": prefix + "-" + letter, "name": "Demo partner " + letter
        }, 201)
        partners[letter] = partner
        print(f"Partner {letter}: {partner['id']}")
    sequence = 0

    def sale(letter, amount, void=False):
        nonlocal sequence
        sequence += 1
        created = request(LEDGER, "/api/v1/sales", "POST", {
            "externalReference": f"{prefix}-{sequence}", "partnerId": partners[letter]["id"],
            "saleDate": period + "-15", "amount": amount
        }, 201)
        print(f"Sale {letter}: {created['id']} amount={amount} void={void}")
        if void:
            request(LEDGER, "/api/v1/sales/" + created["id"] + "/void", "PATCH", {"reason": "Demo customer cancellation"})
        return created

    first = sale("A", 4000000)
    sale("A", 5000000)
    sale("A", 2000000, True)
    sale("B", 10000000)
    sale("B", 5000000)
    sale("C", 50000000)
    sale("D", 1000000, True)
    print("REPORT BEFORE:", json.dumps(request(LEDGER, report_path), indent=2))
    run = request(LEDGER, "/api/v1/settlements", "POST", {"period": period}, 201)
    print("SETTLEMENT:", json.dumps(run, indent=2))
    for field, expected in {"partnerCount": 3, "validSalesCount": 5, "totalSales": 74000000, "totalCommission": 2630000}.items():
        equal(run[field], expected, field)
    entries = request(LEDGER, "/api/v1/settlements/" + run["id"] + "/entries")
    equal(entries["totalElements"], 3, "entry count")
    for entry, amount, rate in zip(entries["content"], (180000, 450000, 2000000), ("0.0200", "0.0300", "0.0400")):
        equal(entry["commissionAmount"], amount, "commission")
        equal(entry["commissionRate"], rate, "rate")
    after = request(LEDGER, report_path)
    print("REPORT AFTER:", json.dumps(after, indent=2))
    equal(after["totalElements"], 4, "report count")
    for row, letter, rank in zip(after["content"], "CBAD", (1, 2, 3, 4)):
        if row["partnerId"] != partners[letter]["id"] or row["salesRank"] != rank:
            raise RuntimeError("Unexpected report rank or order")
    if after["content"][-1]["settlementStatus"] != "EXCLUDED" or after["content"][-1]["settledCommissionAmount"] is not None:
        raise RuntimeError("All-VOID partner must be EXCLUDED with null commission")
    request(LEDGER, "/api/v1/settlements", "POST", {"period": period}, 409)
    request(LEDGER, "/api/v1/sales/" + first["id"] + "/void", "PATCH", {"reason": "Attempt after closure"}, 409)
    request(LEDGER, "/api/v1/sales", "POST", {
        "externalReference": prefix + "-closed", "partnerId": partners["A"]["id"],
        "saleDate": period + "-15", "amount": 1
    }, 409)
    print(f"DEMO PASS: settlement={run['id']} period={period}. Re-running this month intentionally fails; no automatic reset.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("period", nargs="?", default=previous_month())
    parser.add_argument("--smoke", action="store_true")
    args = parser.parse_args()
    try:
        smoke() if args.smoke else demo(args.period)
    except (RuntimeError, urllib.error.URLError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)