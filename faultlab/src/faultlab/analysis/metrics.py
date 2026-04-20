import logging
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import TYPE_CHECKING

import pandas as pd

if TYPE_CHECKING:
    from faultlab.db.transactions import TransactionsRepository

logger = logging.getLogger(__name__)

DEFAULT_RESULTS_DIR = Path("tests").joinpath("results")


@dataclass
class TransactionMetrics:
    total_transactions: int = 0
    successful: int = 0
    success_rate: float = 0.0
    avg_latency_s: float = 0.0
    p50_latency_s: float = 0.0
    p95_latency_s: float = 0.0
    p99_latency_s: float = 0.0
    throughput_tps: float = 0.0
    avg_retries: float = 0.0

    def log(self) -> None:
        logger.info(f"Success rate:           {self.success_rate:.1f}% ({self.successful}/{self.total_transactions})")
        logger.info(f"Avg latency:            {self.avg_latency_s:.3f}s")
        logger.info(f"p50 latency:            {self.p50_latency_s:.3f}s")
        logger.info(f"p95 latency:            {self.p95_latency_s:.3f}s")
        logger.info(f"p99 latency:            {self.p99_latency_s:.3f}s")
        logger.info(f"Throughput:             {self.throughput_tps:.2f} tx/s")
        logger.info(f"Avg retries:            {self.avg_retries:.2f}")


def compute_metrics(transactions_repo: "TransactionsRepository", start_time: datetime) -> TransactionMetrics:
    df = transactions_repo.fetch_transactions_since(start_time)

    if df.empty:
        logger.warning("No transactions found for metrics computation")
        return TransactionMetrics()

    # TODO change updated add to new `confirmed_at` column for better precision
    df["latency_s"] = (df["updated_at"] - df["created_at"]).dt.total_seconds()

    successful = (df["status"] == "CONFIRMED").sum()
    total = len(df)

    # Throughput: total confirmed transactions / time span
    time_span_s = (df["created_at"].max() - df["created_at"].min()).total_seconds()
    throughput_tps = successful / max(time_span_s, 1)

    latencies = df["latency_s"].dropna()

    return TransactionMetrics(
        total_transactions=total,
        successful=successful,
        success_rate=(successful / total * 100) if total > 0 else 0.0,
        avg_latency_s=float(latencies.mean()) if len(latencies) > 0 else 0.0,
        p50_latency_s=float(latencies.quantile(0.50)) if len(latencies) > 0 else 0.0,
        p95_latency_s=float(latencies.quantile(0.95)) if len(latencies) > 0 else 0.0,
        p99_latency_s=float(latencies.quantile(0.99)) if len(latencies) > 0 else 0.0,
        throughput_tps=throughput_tps,
        avg_retries=float(df["retry_count"].mean()),
    )


def save_metrics_csv(name: str, transactions_repo: "TransactionsRepository", start_time: datetime) -> Path:
    DEFAULT_RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    df = transactions_repo.fetch_transactions_since(start_time)

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    filename = DEFAULT_RESULTS_DIR.joinpath(f"{name}_{timestamp}.csv")
    df.to_csv(filename, index=False)
    logger.info(f"Transaction data saved to {filename}")

    return filename
