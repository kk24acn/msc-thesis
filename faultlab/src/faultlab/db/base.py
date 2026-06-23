from abc import ABC, abstractmethod

import pandas as pd
from sqlalchemy import create_engine, text


class ABCRepository(ABC):
    def __init__(self, db_url: str, table_name: str):
        self.engine = create_engine(db_url)
        self.table_name = table_name

    @abstractmethod
    def save(self, *args, **kwargs) -> None:
        pass

    def truncate(self) -> int:
        with self.engine.begin() as conn:
            result = conn.execute(text(f"TRUNCATE TABLE {self.table_name} RESTART IDENTITY"))
            return result.rowcount

    def count(self) -> int:
        with self.engine.connect() as conn:
            return conn.execute(text(f"SELECT COUNT(*) FROM {self.table_name}")).scalar_one()

    def fetch_all(self) -> pd.DataFrame:
        with self.engine.connect() as conn:
            return pd.read_sql_table(self.table_name, conn)
