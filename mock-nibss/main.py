import asyncio
import hashlib
import hmac
import json
import os
import sqlite3
from contextlib import asynccontextmanager
from datetime import datetime, timezone

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Config from environment
NIBSS_WEBHOOK_SECRET = os.getenv("NIBSS_WEBHOOK_SECRET", "nibss-webhook-secret-dev")
NEXUS_BANK_CALLBACK_URL = os.getenv(
    "NEXUS_BANK_CALLBACK_URL",
    "http://localhost:8080/api/webhooks/nibss/transfer-out/callback",
)
CALLBACK_DELAY_SECONDS = int(os.getenv("CALLBACK_DELAY_SECONDS", "5"))
DB_PATH = os.getenv("DB_PATH", "nibss.db")


def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with get_db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS transfers (
                nip_session_id    TEXT PRIMARY KEY,
                amount            REAL,
                from_account_number TEXT,
                to_account_number TEXT,
                target_bank_code  TEXT,
                target_account_name TEXT,
                description       TEXT,
                status            TEXT DEFAULT 'PENDING',
                outcome           TEXT,
                created_at        TEXT,
                resolved_at       TEXT
            )
            """
        )
        conn.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="Mock NIBSS", lifespan=lifespan)


# --- Pydantic models ---

class TransferSubmission(BaseModel):
    nipSessionId: str
    amount: float
    fromAccountNumber: str
    toAccountNumber: str
    targetBankCode: str
    targetAccountName: str
    description: str | None = None


class ResolveRequest(BaseModel):
    outcome: str  # SUCCESS or FAILED


# --- Helpers ---

def compute_signature(body: bytes) -> str:
    digest = hmac.new(
        NIBSS_WEBHOOK_SECRET.encode(),
        body,
        hashlib.sha256,
    ).hexdigest()
    return f"sha256={digest}"


def determine_outcome(to_account_number: str) -> str:
    return "FAILED" if to_account_number.startswith("0000") else "SUCCESS"


async def send_callback(
    nip_session_id: str,
    outcome: str,
    amount: float,
    to_account_number: str,
    target_bank_code: str,
) -> None:
    response_code = "96" if outcome == "FAILED" else "00"
    payload = {
        "nipSessionId": nip_session_id,
        "status": outcome,
        "responseCode": response_code,
        "amount": amount,
        "destinationAccountNumber": to_account_number,
        "destinationBankCode": target_bank_code,
    }
    body = json.dumps(payload).encode()
    signature = compute_signature(body)

    async with httpx.AsyncClient() as client:
        response = await client.post(
            NEXUS_BANK_CALLBACK_URL,
            content=body,
            headers={
                "Content-Type": "application/json",
                "X-NIBSS-Signature": signature,
            },
            timeout=10.0,
        )
        response.raise_for_status()

    db_status = "COMPLETED" if outcome == "SUCCESS" else "FAILED"
    with get_db() as conn:
        conn.execute(
            "UPDATE transfers SET status = ?, outcome = ?, resolved_at = ? WHERE nip_session_id = ?",
            (db_status, outcome, datetime.now(timezone.utc).isoformat(), nip_session_id),
        )
        conn.commit()


async def auto_callback(
    nip_session_id: str,
    outcome: str,
    amount: float,
    to_account_number: str,
    target_bank_code: str,
) -> None:
    await asyncio.sleep(CALLBACK_DELAY_SECONDS)

    # Skip if already resolved by a manual override
    with get_db() as conn:
        row = conn.execute(
            "SELECT status FROM transfers WHERE nip_session_id = ?",
            (nip_session_id,),
        ).fetchone()

    if row is None or row["status"] != "PENDING":
        return

    try:
        await send_callback(nip_session_id, outcome, amount, to_account_number, target_bank_code)
    except Exception as exc:
        print(f"[auto_callback] Failed for {nip_session_id}: {exc}")


# --- Endpoints ---

@app.post("/transfers", status_code=201)
async def submit_transfer(submission: TransferSubmission):
    outcome = determine_outcome(submission.toAccountNumber)
    now = datetime.now(timezone.utc).isoformat()

    try:
        with get_db() as conn:
            conn.execute(
                """
                INSERT INTO transfers (
                    nip_session_id, amount, from_account_number, to_account_number,
                    target_bank_code, target_account_name, description,
                    status, outcome, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """,
                (
                    submission.nipSessionId,
                    submission.amount,
                    submission.fromAccountNumber,
                    submission.toAccountNumber,
                    submission.targetBankCode,
                    submission.targetAccountName,
                    submission.description,
                    outcome,
                    now,
                ),
            )
            conn.commit()
    except sqlite3.IntegrityError:
        raise HTTPException(status_code=409, detail="Duplicate nip_session_id")

    asyncio.create_task(
        auto_callback(
            submission.nipSessionId,
            outcome,
            submission.amount,
            submission.toAccountNumber,
            submission.targetBankCode,
        )
    )

    return {"nipSessionId": submission.nipSessionId, "status": "PENDING", "outcome": outcome}


@app.post("/admin/transfers/{nip_session_id}/resolve")
async def manual_resolve(nip_session_id: str, body: ResolveRequest):
    outcome = body.outcome.upper()
    if outcome not in ("SUCCESS", "FAILED"):
        raise HTTPException(status_code=400, detail="outcome must be SUCCESS or FAILED")

    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM transfers WHERE nip_session_id = ?",
            (nip_session_id,),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=404, detail="Transfer not found")

    if row["status"] != "PENDING":
        raise HTTPException(status_code=409, detail=f"Transfer already resolved: {row['status']}")

    try:
        await send_callback(
            nip_session_id,
            outcome,
            row["amount"],
            row["to_account_number"],
            row["target_bank_code"],
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Callback failed: {exc}")

    return {"nipSessionId": nip_session_id, "outcome": outcome}


@app.get("/transfers/{nip_session_id}")
def get_transfer(nip_session_id: str):
    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM transfers WHERE nip_session_id = ?",
            (nip_session_id,),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=404, detail="Transfer not found")

    return dict(row)
