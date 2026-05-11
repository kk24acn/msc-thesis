from sqlalchemy import Table, Column, String, Integer, MetaData
from sqlalchemy.dialects.postgresql import insert as pg_insert

from faultlab.db.base import ABCRepository


class MpcKeysRepository(ABCRepository):
    def __init__(self, db_url: str):
        super().__init__(db_url, "mpc_keys")
        metadata = MetaData()
        self.mpc_keys = Table(
            "mpc_keys",
            metadata,
            Column("key_id", String(255), primary_key=True),
            Column("ethereum_address", String(42), nullable=False, unique=True),
            Column("threshold", Integer, nullable=False),
            Column("total_parties", Integer, nullable=False),
            Column("derivation_path", String(255), nullable=True),
        )

    def save(
        self,
        key_id: str,
        address: str,
        threshold: int,
        total_parties: int,
        derivation_path: str | None = None,
    ) -> None:
        statement = pg_insert(self.mpc_keys).values(
            key_id=key_id,
            ethereum_address=address,
            threshold=threshold,
            total_parties=total_parties,
            derivation_path=derivation_path,
        )
        with self.engine.begin() as conn:
            conn.execute(statement)
