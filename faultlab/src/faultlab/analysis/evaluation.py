import re
from pathlib import Path
import numpy as np
import pandas as pd

from .loader import load_csv
from .metrics import execution_summary, status_summary
from .constants import DSG_TOTAL_ROUNDS, QUARANTINE_MODES


def get_base_01_summary(dir: Path) -> dict[str, float]:
    if not dir.exists():
        return {"e2e_tps": 0.0, "signing_tps": 0.0, "hot_signing_tps": 0.0}

    e2e_list, sign_list, hot_list = [], [], []

    for file_path in sorted(dir.glob("*.csv")):
        if "circuit_breaker" in file_path.name:
            df = load_csv(file_path)
            if df.empty:
                continue
            sub = df[df["status"] == "CONFIRMED"].copy()
            if sub.empty:
                continue

            t0 = df["created_at"].min()
            sub["sec"] = (sub["signed_at"] - t0).dt.total_seconds()
            sub = sub.sort_values("sec")

            dur_e2e = (df["confirmed_at"].max() - t0).total_seconds()
            if dur_e2e > 0:
                e2e_list.append(len(sub) / dur_e2e)

            dur_sign = sub["sec"].max()
            if dur_sign > 0:
                sign_list.append(len(sub) / dur_sign)

            # Calculate Hot-Phase Signing TPS across the last 500 transactions
            if len(sub) > 500:
                t_500 = sub.iloc[499]["sec"]
                t_end = sub.iloc[-1]["sec"]
                dur_hot = t_end - t_500
                if dur_hot > 0:
                    hot_list.append((len(sub) - 500.0) / dur_hot)

    return {
        "e2e_tps": round(float(pd.Series(e2e_list).median()), 2) if e2e_list else 0.0,
        "signing_tps": round(float(pd.Series(sign_list).median()), 2) if sign_list else 0.0,
        "hot_signing_tps": round(float(pd.Series(hot_list).median()), 2) if hot_list else 0.0,
    }


def get_crash_01_summary_df(dir: Path, base_tps: float) -> pd.DataFrame:
    if not dir.exists() or base_tps <= 0:
        return pd.DataFrame()

    pattern = re.compile(r"quarantine_(disabled|circuit_breaker)")
    runs = {"DISABLED": [], "CIRCUIT_BREAKER": []}

    for file_path in sorted(dir.glob("*.csv")):
        match = pattern.search(file_path.name)
        if not match:
            continue
        mode = QUARANTINE_MODES.get(match.group(1), match.group(1))
        df = load_csv(file_path)
        sub = df[df["status"] == "CONFIRMED"].copy()
        t0 = df["created_at"].min()
        dur = (sub["signed_at"].max() - t0).total_seconds()
        if dur > 0:
            runs[mode].append(len(sub) / dur)

    records = []
    for mode, tps_list in runs.items():
        if not tps_list:
            continue
        med_tps = float(pd.Series(tps_list).median())
        deg = max(0.0, ((base_tps - med_tps) / base_tps) * 100.0)
        records.append(
            {
                "Quarantine Mode": mode,
                "Signing Throughput (TPS)": round(med_tps, 2),
                "Throughput Degradation (%)": round(deg, 2),
            }
        )

    return pd.DataFrame(records)


def get_latency_01_summary_df(dir: Path) -> pd.DataFrame:
    if not dir.exists():
        return pd.DataFrame()

    pattern = re.compile(r"quarantine_(disabled|circuit_breaker)_(\d+)_")
    records = []

    for file_path in sorted(dir.glob("*.csv")):
        match = pattern.search(file_path.name)
        if not match:
            continue
        mode = QUARANTINE_MODES.get(match.group(1), match.group(1))
        delay = int(match.group(2))
        df = load_csv(file_path)
        sub = df[df["status"] == "CONFIRMED"]
        if sub.empty:
            continue

        queued = sub["signing_started_at"] - sub["created_at"]
        signing = sub["signed_at"] - sub["signing_started_at"]
        submission = sub["submitted_at"] - sub["signed_at"]
        confirmation = sub["confirmed_at"] - sub["submitted_at"]

        records.append(
            {
                "Mode": mode,
                "Delay (ms)": delay,
                "Queued (s)": round(queued.dt.total_seconds().median(), 2),
                "Signing (s)": round(signing.dt.total_seconds().median(), 2),
                "Submission (s)": round(submission.dt.total_seconds().median(), 2),
                "Confirmation (s)": round(confirmation.dt.total_seconds().median(), 2),
            }
        )

    df_out = pd.DataFrame(records)
    if df_out.empty:
        return pd.DataFrame()
    return df_out.groupby(["Mode", "Delay (ms)"]).median().reset_index().sort_values(["Mode", "Delay (ms)"])


def get_trans_01_summary_df(dir: Path, base_tps: float) -> pd.DataFrame:
    if not dir.exists():
        return pd.DataFrame()

    pattern = re.compile(r"quarantine_(disabled|soft|circuit_breaker)_(\d+)_")
    runs = {}

    for file_path in sorted(dir.glob("*.csv")):
        match = pattern.search(file_path.name)
        if not match:
            continue
        mode = QUARANTINE_MODES.get(match.group(1), match.group(1))
        drop_rate = int(match.group(2))
        key = (mode, drop_rate)
        if key not in runs:
            runs[key] = []

        df = load_csv(file_path)
        sub = df[df["status"] == "CONFIRMED"]
        t0 = df["created_at"].min()
        dur = (sub["signed_at"].max() - t0).total_seconds()
        if dur > 0:
            runs[key].append(
                (len(sub) / dur, (len(sub) / len(df)) * 100.0)
            )  # v[0] = Signing Throughput, v[1] = Failover Success Rate

    records = []
    for (mode, drop_rate), vals in sorted(runs.items(), key=lambda x: (x[0][0], x[0][1])):
        tps_med = float(pd.Series([v[0] for v in vals]).median())
        succ_med = float(pd.Series([v[1] for v in vals]).median())
        deg = max(0.0, ((base_tps - tps_med) / base_tps) * 100.0) if base_tps > 0 else 0.0
        records.append(
            {
                "Quarantine Mode": mode,
                "Failure Rate (% per round)": drop_rate,
                "Cumulative Failure Rate (% per transaction)": round(
                    ((1.0 - (1.0 - drop_rate / 100) ** DSG_TOTAL_ROUNDS) * 100), 2
                ),
                "Signing Throughput (TPS)": round(tps_med, 2),
                "Failover Success Rate (%)": round(succ_med, 2),
                "Throughput Degradation (%)": round(deg, 2),
            }
        )

    return pd.DataFrame(records)


def get_trans_02_summary_df(dir: Path, base_hot_tps: float) -> pd.DataFrame:
    if not dir.exists():
        return pd.DataFrame()

    pattern = re.compile(r"quarantine_(disabled|soft|circuit_breaker)")
    runs_by_mode = {"DISABLED": [], "SOFT": [], "CIRCUIT_BREAKER": []}

    for file_path in sorted(dir.glob("*.csv")):
        match = pattern.search(file_path.name)
        if not match:
            continue
        mode = QUARANTINE_MODES.get(match.group(1), match.group(1))
        df = load_csv(file_path)
        sub = df[df["status"] == "CONFIRMED"].copy()
        if sub.empty:
            continue

        t0 = df["created_at"].min()
        sub["seconds_from_start"] = (sub["signed_at"] - t0).dt.total_seconds()
        sub = sub.sort_values("seconds_from_start")
        runs_by_mode[mode].append(sub)

    records = []
    for mode in ["DISABLED", "SOFT", "CIRCUIT_BREAKER"]:
        runs = runs_by_mode[mode]
        if not runs:
            continue

        med_times = np.median([r["seconds_from_start"].values for r in runs], axis=0)

        med_fault = float(med_times[499])
        med_batch = float(med_times[-1])

        post_fault_dur = med_batch - med_fault
        optimal_dur = 500.0 / base_hot_tps
        scalar_mttr = post_fault_dur - optimal_dur
        med_post_rate = 500.0 / post_fault_dur

        records.append(
            {
                "Quarantine Mode": mode,
                "Fault Lifted at Tx 500 (s)": round(med_fault, 2),
                "Batch Completion (s)": round(med_batch, 2),
                "Post-Outage Duration (s)": round(post_fault_dur, 2),
                "Optimal Duration (s)": round(optimal_dur, 2),
                "Scalar MTTR (s)": round(scalar_mttr, 2),
                "Post-Recovery Rate (tx/s)": round(med_post_rate, 2),
            }
        )

    return pd.DataFrame(records)


def get_edge_case_boundary_matrix(edge_cases_dir: Path, base_01_dir: Path) -> pd.DataFrame:
    EDGE_CASE_CONFIGS = [
        ("BYZ_01", "*.csv", "BYZ-01: MUTATE"),
        ("BYZ_02", "*.csv", "BYZ-02: REPLAY"),
        ("DROP_01", "*.csv", "DROP-01: SILENT_DROP_RES (Round 4)"),
        ("SWEEP_01", "*CRASH_RES*.csv", "SWEEP-01: CRASH_RES"),
        ("SWEEP_01", "*MUTATE*.csv", "SWEEP-01: MUTATE"),
        ("SWEEP_01", "*SILENT_DROP_RES*.csv", "SWEEP-01: SILENT_DROP_RES"),
    ]

    if not edge_cases_dir.exists():
        return pd.DataFrame()

    base_tps = get_base_01_summary(base_01_dir)["signing_tps"]

    records = []
    for test_folder, file_pattern, test_name in EDGE_CASE_CONFIGS:
        target_dir = edge_cases_dir.joinpath(test_folder)
        if not target_dir.exists():
            continue

        csv_files = sorted(list(target_dir.glob(file_pattern)))
        if not csv_files:
            continue

        total_txs = 0
        confirmed_std = 0
        confirmed_swept = 0
        signing_retries = 0
        e2e_medians = []
        signing_queue_medians = []
        tps_list = []

        for file_path in csv_files:
            df = load_csv(file_path)
            if df.empty:
                continue

            exec_sum = execution_summary(df)
            stat_sum = status_summary(df)

            total_txs += exec_sum.get("total_txs", 0)
            confirmed_std += stat_sum.get("CONFIRMED", 0)
            confirmed_swept += stat_sum.get("CONFIRMED_SWEEPED", 0)
            signing_retries += int(df["signing_retries"].sum())

            e2e_medians.append(exec_sum["e2e_median_s"])
            signing_queue_medians.append(exec_sum["signing_queue_median_s"])

            sub = df[df["status"] == "CONFIRMED"].copy()
            if not sub.empty:
                t0 = df["created_at"].min()
                dur = (sub["signed_at"].max() - t0).total_seconds()
                if dur > 0:
                    tps_list.append(len(sub) / dur)

        success_rate = (confirmed_std / total_txs * 100.0) if total_txs > 0 else 0.0
        med_tps = float(pd.Series(tps_list).median()) if tps_list else 0.0
        deg = max(0.0, ((base_tps - med_tps) / base_tps) * 100.0) if base_tps > 0 else 0.0
        med_e2e = round(float(pd.Series(e2e_medians).median()), 2) if e2e_medians else 0.0
        med_sign = round(float(pd.Series(signing_queue_medians).median()), 2) if signing_queue_medians else 0.0

        records.append(
            {
                "Test Case": test_name,
                "Total Transactions": total_txs,
                "Failover Success Rate (%)": round(success_rate, 2),
                "Signing Throughput (TPS)": round(med_tps, 2),
                "Throughput Degradation (%)": round(deg, 2),
                "Confirmed (Standard)": confirmed_std,
                "Confirmed (Swept)": confirmed_swept,
                "Signing Retries": signing_retries,
                "Median E2E Latency (s)": med_e2e,
                "Median Signing Queue (s)": med_sign,
            }
        )

    return pd.DataFrame(records)
