from typing import Dict

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Config(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    DATABASE_URL: str = ""
    ORCHESTRATOR_URL: str = ""
    SIGNER_GRPC_URLS: list[str] = Field(default_factory=list)
    PROXY_CONTROL_PLANE_URLS: list[str] = Field(default_factory=list)

    DKG_THRESHOLD: int = 2
    DKG_TOTAL_PARTIES: int = 3
    DKG_SESSIONS: int = 10
    DKG_DERIVATION_PATH: str = "m/0"

    HARDHAT_RPC_URL: str = "http://localhost:8545"
    HARDHAT_CHAIN_ID: int = 31337
    HARDHAT_GAS_PRICE_GWEI: float = 2.0

    FUNDING_PRIVATE_KEYS: list[str] = Field(default_factory=list)
    FUNDING_AMOUNT_ETH: str = "1000.0"

    HTTP_TIMEOUT: int = 30
    DOCKER_PROJECT_NAME: str = ""


settings = Config()
