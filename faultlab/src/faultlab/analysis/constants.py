ALL_STATUSES: list[str] = [
    "NEW",
    "SIGNING",
    "SIGNED",
    "SUBMITTING",
    "IN_MEMPOOL",
    "CONFIRMED",
    "CONFIRMED_SWEEPED",
    "FAILED",
    "CRYPTOGRAPHIC_ABORT",
    "VERIFICATION_ABORT",
]

TERMINAL_STATUSES: frozenset[str] = frozenset({"CONFIRMED", "CONFIRMED_SWEEPED"})

DATETIME_COLS: list[str] = [
    "created_at",
    "signing_started_at",
    "signed_at",
    "submitted_at",
    "confirmed_at",
    "failed_at",
    "updated_at",
    "first_fault_at",
]

NUMERIC_COLS: tuple[str, ...] = (
    "signing_retries",
    "submission_retries",
    "nonce",
    "submission_block",
    "mined_block",
    "version",
    "sweeper_attempts",
    "sweeper_signing_retries",
)

LATENCY_COLS: tuple[str, ...] = (
    "signing_latency_s",
    "pure_signing_latency_s",
    "submission_latency_s",
    "confirmation_latency_s",
    "e2e_latency_s",
)

QUARANTINE_MODES: dict[str, str] = {
    "disabled": "DISABLED",
    "soft": "SOFT",
    "circuit_breaker": "CIRCUIT_BREAKER",
}

QUARANTINE_COLORS: dict[str, str] = {
    "DISABLED": "#e64e83",
    "SOFT": "#2ecc71",
    "CIRCUIT_BREAKER": "#2b5c8f",
}

HOT_PHASE_START_SECONDS: float = 4.0
DSG_TOTAL_ROUNDS = 4  # 4 ExecuteDsgPhase (1 Init + 3 Advance)
