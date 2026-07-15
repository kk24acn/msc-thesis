import numpy as np
import pandas as pd
from .constants import ALL_STATUSES, LATENCY_COLS, TERMINAL_STATUSES


def _safe_scalar(v):
    if isinstance(v, (list, tuple, pd.Series, np.ndarray)):
        # Convert to pure scalar
        return float(v[0]) if len(v) > 0 else float("nan")
    return v


def describe_latency_percentiles(df: pd.DataFrame, status: frozenset[str] = TERMINAL_STATUSES) -> pd.DataFrame:
    if df.empty:
        return pd.DataFrame()

    cols = [c for c in LATENCY_COLS if c in df.columns]
    filtered = df[df["status"].isin(status)]

    if filtered.empty or not cols:
        return pd.DataFrame()

    return filtered[cols].describe(percentiles=[0.50, 0.75, 0.90, 0.95, 0.99])


def execution_summary(df: pd.DataFrame) -> dict:
    if df.empty:
        return {}

    confirmed_df = df[df["status"] == "CONFIRMED"]
    swept_df = df[df["status"] == "CONFIRMED_SWEEPED"]

    valid_e2e = confirmed_df["e2e_latency_s"].dropna()
    total_time_span_s = valid_e2e.max() if not valid_e2e.empty else 0
    overall_tps = len(confirmed_df) / total_time_span_s if total_time_span_s > 0 else 0.0

    def _med(col: str) -> float:
        if col in confirmed_df.columns and confirmed_df[col].notna().any():
            return float(confirmed_df[col].median())
        return float("nan")

    return {
        "total_txs": len(df),
        "confirmed_txs": len(confirmed_df),
        "swept_txs": len(swept_df),
        "success_rate": round(len(confirmed_df) / len(df) * 100, 2) if len(df) > 0 else float("nan"),
        "overall_tps": round(overall_tps, 2),
        "e2e_median_s": _med("e2e_latency_s"),
        "pure_sign_median_s": _med("pure_signing_latency_s"),
        "signing_queue_median_s": _med("signing_latency_s"),
        "submission_median_s": _med("submission_latency_s"),
        "confirmation_median_s": _med("confirmation_latency_s"),
    }


def aggregate_summaries(summaries: list[dict]) -> dict:
    if not summaries:
        return {}

    safe_summaries = [{k: _safe_scalar(v) for k, v in s.items()} for s in summaries]
    metrics_df = pd.DataFrame(safe_summaries)

    result: dict = {"n_runs": len(safe_summaries)}

    for col in metrics_df.columns:
        series = pd.to_numeric(metrics_df[col], errors="coerce").dropna()  # coerce -> convert non-numeric vals to NaN
        if series.empty:
            continue

        if col in ("total_txs", "confirmed_txs", "swept_txs"):
            result[col] = int(series.sum())
            continue

        if col == "success_rate":
            total = result.get("total_txs", 0)
            conf = result.get("confirmed_txs", 0)
            result["success_rate_mean"] = round((conf / total) * 100, 2) if total > 0 else float("nan")
            result["success_rate_std"] = round(series.std(), 2) if len(series) > 1 else 0.0
            continue

        result[f"{col}_mean"] = round(series.mean(), 2)
        result[f"{col}_std"] = round(series.std(), 2)

    return result


def status_summary(df: pd.DataFrame) -> dict:
    if df.empty:
        return {}

    counts = df["status"].value_counts().to_dict()
    for status in ALL_STATUSES:
        counts.setdefault(status, 0)
    return counts
