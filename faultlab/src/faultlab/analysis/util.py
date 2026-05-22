from pathlib import Path
from datetime import datetime

import pandas as pd

ALL_STATUSES: list[str] = [
    "NEW",
    "SIGNING",
    "SIGNED",
    "SUBMITTING",
    "IN_MEMPOOL",
    "CONFIRMED",
    "STALLED",
    "FAILED",
]

TERMINAL_STATUSES: frozenset[str] = frozenset({"CONFIRMED", "STALLED", "FAILED", "CRYPTOGRAPHIC_ABORT", "VERIFICATION_ABORT"}) # fmt: skip
NON_TERMINAL_STATUSES: frozenset[str] = frozenset({"NEW", "SIGNING", "SIGNED", "SUBMITTING", "IN_MEMPOOL"})

DATETIME_COLS: list[str] = [
    "created_at",
    "signing_started_at",
    "signed_at",
    "submitted_at",
    "confirmed_at",
    "failed_at",
    "updated_at",
]


def save_transactions_csv(df: pd.DataFrame, results_dir: Path | str, name: str) -> Path:
    results_dir = Path(results_dir)
    results_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = results_dir.joinpath(f"{name}_{timestamp}.csv")
    df.to_csv(path, index=False)
    return path
