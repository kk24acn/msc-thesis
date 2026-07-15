from datetime import datetime
from pathlib import Path
import pandas as pd
from .constants import DATETIME_COLS, NUMERIC_COLS


def enrich_latency_metrics(df: pd.DataFrame) -> pd.DataFrame:
    def _diff_s(end_col: str, start_col: str) -> pd.Series:
        if end_col in df.columns and start_col in df.columns:
            return (df[end_col] - df[start_col]).dt.total_seconds()
        return pd.Series([float("nan")] * len(df))

    df["signing_latency_s"] = _diff_s("signed_at", "created_at")
    df["pure_signing_latency_s"] = _diff_s("signed_at", "signing_started_at")
    df["submission_latency_s"] = _diff_s("submitted_at", "signed_at")
    df["confirmation_latency_s"] = _diff_s("confirmed_at", "submitted_at")
    df["e2e_latency_s"] = _diff_s("confirmed_at", "created_at")

    return df


def load_csv(csv_path: Path) -> pd.DataFrame:
    try:
        df = pd.read_csv(csv_path)
    except Exception as e:
        print(f"Error reading {csv_path}: {e}")
        return pd.DataFrame()

    for col in DATETIME_COLS:
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], errors="coerce", utc=True)

    for col in NUMERIC_COLS:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    return enrich_latency_metrics(df)


def save_transactions_csv(df: pd.DataFrame, results_dir: Path | str, name: str) -> Path:
    results_dir = Path(results_dir)
    results_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = results_dir.joinpath(f"{name}_{timestamp}.csv")
    df.to_csv(path, index=False)
    return path
