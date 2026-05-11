from datetime import datetime

import pandas as pd
from sqlalchemy import text

from faultlab.db.base import ABCRepository


class TransactionsRepository(ABCRepository):
    def __init__(self, db_url: str):
        super().__init__(db_url, "transactions")

    def save(self, **kwargs) -> None:
        raise NotImplementedError("Transactions table updates are restricted from faultlab")

    def fetch_transactions_since(self, start_time: datetime) -> pd.DataFrame:
        query = """
        SELECT * FROM transactions
        WHERE created_at >= :start_time
        ORDER BY created_at
        """
        with self.engine.connect() as conn:
            return pd.read_sql(text(query), conn, params={"start_time": start_time})
