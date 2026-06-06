import re
from collections import defaultdict
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
    "CONFIRMED_SWEEPED",
    "STALLED",
    "FAILED",
    "CRYPTOGRAPHIC_ABORT",
]
SUCCESS_STATUSES: frozenset[str] = frozenset(["CONFIRMED", "CONFIRMED_SWEEPED"])
FAILURE_STATUSES: frozenset[str] = frozenset(["FAILED", "CRYPTOGRAPHIC_ABORT", "VERIFICATION_ABORT"])
TERMINAL_STATUSES: frozenset[str] = frozenset({"CONFIRMED", "CONFIRMED_SWEEPED", "STALLED", "FAILED", "CRYPTOGRAPHIC_ABORT", "VERIFICATION_ABORT"}) # fmt: skip
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

NUMERIC_COLS: tuple[str, ...] = (
    "signing_retries",
    "submission_retries",
    "nonce",
    "submission_block",
    "mined_block",
    "version",
)

LATENCY_COLS: tuple[str, ...] = (
    "signing_latency_s",
    "pure_signing_latency_s",
    "submission_latency_s",
    "confirmation_latency_s",
    "e2e_latency_s",
)

STATUS_COLORS: dict[str, str] = {
    "NEW": "#4fc3f7",
    "SIGNING": "#9575cd",
    "SIGNED": "#5614c7",
    "SUBMITTING": "#ffb300",
    "IN_MEMPOOL": "#ff7043",
    "CONFIRMED": "#388e3c",
    "CONFIRMED_SWEEPED": "#fff700",
    "STALLED": "#2c6e8f",
    "FAILED": "#d32f2f",
    "CRYPTOGRAPHIC_ABORT": "#880e4f",
}

# == FILES =====================================================================


def save_transactions_csv(df: pd.DataFrame, results_dir: Path | str, name: str) -> Path:
    results_dir = Path(results_dir)
    results_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = results_dir.joinpath(f"{name}_{timestamp}.csv")
    df.to_csv(path, index=False)
    return path


def load_csv(path: Path | str) -> pd.DataFrame:
    df = pd.read_csv(path)

    for col in DATETIME_COLS:
        if col in df.columns:
            df[col] = pd.to_datetime(df[col], utc=True, errors="coerce")

    for col in NUMERIC_COLS:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    return df


# == DATAFRAMES =====================================================================


def enrich_latency_metrics(df: pd.DataFrame) -> pd.DataFrame:
    latency_mappings = {
        "signing_latency_s": ("signed_at", "created_at"),
        "pure_signing_latency_s": ("signed_at", "signing_started_at"),
        "submission_latency_s": ("submitted_at", "signed_at"),
        "confirmation_latency_s": ("confirmed_at", "submitted_at"),
        "e2e_latency_s": ("confirmed_at", "created_at"),
    }

    for metric_name, (end_col, start_col) in latency_mappings.items():
        if start_col in df.columns and end_col in df.columns:
            df[metric_name] = (df[end_col] - df[start_col]).dt.total_seconds()

    return df


def describe_latency_percentiles(df: pd.DataFrame, status: frozenset[str] = SUCCESS_STATUSES) -> pd.DataFrame:
    cols = [c for c in LATENCY_COLS if c in df.columns]
    filtered = df[df["status"].isin(status)]
    return filtered[cols].describe(percentiles=[0.50, 0.75, 0.90, 0.95, 0.99])


def execution_summary(df):
    df = enrich_latency_metrics(df.copy())

    df_confirmed = df[df["status"].isin(["CONFIRMED", "CONFIRMED_SWEEPED"])]

    input_duration = (df["created_at"].max() - df["created_at"].min()).total_seconds()
    total_duration = (df["updated_at"].max() - df["created_at"].min()).total_seconds()

    data = {
        "Total Transactions": len(df),
        "Success Rate (%)": (len(df_confirmed) / len(df)) * 100,
        "Submission Rate (TPS)": len(df) / input_duration if input_duration > 0 else 0,
        "Throughput (TPS)": len(df_confirmed) / total_duration if total_duration > 0 else 0,
        "Avg Signing Retries": df["signing_retries"].mean(),
        "Avg Submission Retries": df["submission_retries"].mean(),
        "Unique From Addresses": df["from_address"].nunique(),
        "Unique To Addresses": df["to_address"].nunique(),
        "Mean E2E Latency (s)": df_confirmed["e2e_latency_s"].mean(),
        "Median E2E Latency (s)": df_confirmed["e2e_latency_s"].median(),
        "Mean Pure Signing Latency (s)": df["pure_signing_latency_s"].mean(),
        "Median Pure Signing Latency (s)": df["pure_signing_latency_s"].median(),
    }

    summary_data = {"Metric": list(data.keys()), "Value": [round(value, 3) for value in data.values()]}

    return pd.DataFrame(summary_data)


def status_summary(df: pd.DataFrame) -> pd.DataFrame:
    counts = df["status"].value_counts().reindex(ALL_STATUSES, fill_value=0)
    pct = (counts / len(df) * 100).round(2)
    return pd.DataFrame(
        {
            "count": counts,
            "%": pct,
            "is_terminal": ["Yes" if s in TERMINAL_STATUSES else "No" for s in counts.index],
        }
    )


# == MULTI-RUN AGGREGATION =====================================================

# Regex matching the ``_YYYYMMDD_HHMMSS`` timestamp suffix that
_TIMESTAMP_SUFFIX_RE = re.compile(r"_\d{8}_\d{6}$")


def merge_run_results(parent_dir: Path | str) -> dict[str, list[pd.DataFrame]]:
    parent_dir = Path(parent_dir)

    run_dirs = sorted(
        [d for d in parent_dir.iterdir() if d.is_dir() and d.name.isdigit()],
        key=lambda d: int(d.name),
    )
    if not run_dirs:
        raise FileNotFoundError(f"No numbered run directories found in {parent_dir}")

    groups: dict[str, list[pd.DataFrame]] = defaultdict(list)

    for run_dir in run_dirs:
        csv_files = sorted(run_dir.glob("*.csv"))
        for csv_path in csv_files:
            prefix = _TIMESTAMP_SUFFIX_RE.sub("", csv_path.stem)
            df = enrich_latency_metrics(load_csv(csv_path))
            groups[prefix].append(df)

    return dict(groups)


def summarise_run(df: pd.DataFrame) -> dict:
    confirmed_df = df[df["status"].isin(["CONFIRMED", "CONFIRMED_SWEEPED"])]

    total_span = (df["updated_at"].max() - df["created_at"].min()).total_seconds()
    overall_tps = len(confirmed_df) / total_span if total_span > 0 else float("nan")

    def _med(col: str) -> float:
        if col in confirmed_df.columns and confirmed_df[col].notna().any():
            return round(confirmed_df[col].median(), 3)
        return float("nan")

    return {
        "total_txs": len(df),
        "confirmed_txs": len(confirmed_df),
        "success_rate": round(len(confirmed_df) / len(df) * 100, 1) if len(df) else float("nan"),
        "overall_tps": round(overall_tps, 3),
        "e2e_median_s": _med("e2e_latency_s"),
        "pure_sign_median_s": _med("pure_signing_latency_s"),
        "signing_queue_median_s": _med("signing_latency_s"),
        "submission_median_s": _med("submission_latency_s"),
        "confirmation_median_s": _med("confirmation_latency_s"),
    }


def aggregate_summaries(summaries: list[dict]) -> dict:
    if not summaries:
        return {}

    result: dict = {"n_runs": len(summaries)}
    metrics_df = pd.DataFrame(summaries)

    for col in metrics_df.columns:
        series = pd.to_numeric(metrics_df[col], errors="coerce").dropna()
        if series.empty:
            continue

        if col in ("total_txs", "confirmed_txs"):
            result[col] = int(series.sum())
            continue

        if col == "success_rate":
            result[col] = round(series.mean(), 1)
            continue

        result[f"{col}_mean"] = round(series.mean(), 3)
        result[f"{col}_std"] = round(series.std(), 3)
        result[f"{col}_min"] = round(series.min(), 3)
        result[f"{col}_max"] = round(series.max(), 3)

    return result
