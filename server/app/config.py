"""Application configuration, sourced from environment variables (12-factor).

Keeping all config in env vars makes the later move to Docker trivial.
"""

import ipaddress
import socket
from functools import lru_cache
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # hide_input_in_errors: a validation error would otherwise echo the raw values,
    # vision_api_key included, into the startup log.
    model_config = SettingsConfigDict(
        env_prefix="OPENCOOK_", env_file=".env", extra="ignore", hide_input_in_errors=True,
    )

    # Where uploaded images, crops and the SQLite database live.
    data_dir: Path = Path("./data")

    # HTTP server bind.
    host: str = "0.0.0.0"
    port: int = 8000

    # Friendly name advertised over mDNS so the app shows it in the server picker.
    server_name: str = socket.gethostname()

    # Vision model used for recipe extraction, reached over the OpenAI chat-completions
    # API (see vision_client.py). The default is a local Ollama; any other compatible
    # server or service works by changing the base URL. qwen2.5vl:7b was validated
    # against real cookbook photos and far outperforms llama3.2-vision:11b on German
    # OCR, multi-recipe pages, rotation and reading printed nutrition.
    vision_base_url: str = "http://localhost:11434/v1"
    vision_model: str = "qwen2.5vl:7b"
    # Only needed by endpoints that require one (hosted services); local servers don't.
    vision_api_key: SecretStr | None = None
    # Extra request fields, passed through as-is (a JSON object in the env var), e.g.
    # {"temperature": 0.0001, "max_tokens": 4000}. Which ones take effect is up to the
    # endpoint, so the user picks them for theirs. The default suits the local Ollama:
    # near-greedy, as qwen2.5vl's Modelfile sets it — /v1 ignores the Modelfile and would
    # otherwise sample at 1.0, which misreads far more — and JSON mode, without which
    # some pages come back as unparseable JSON.
    vision_params: dict[str, Any] = {"temperature": 0.0001, "response_format": {"type": "json_object"}}

    # How often the in-process worker polls the jobs table (seconds).
    worker_poll_interval: float = 2.0

    # Where backup archives are written, and how many to keep (rotation). Point
    # backup_dir at an external/network drive (or copy archives off-box) so a backup
    # survives an OS reinstall. Default: under data_dir (so it is never self-included
    # in an archive, which only contains opencook.db + images/).
    backup_dir: Path | None = None
    backup_keep: int = 14

    # Admin password bootstrap. Applied at startup only when no password is set yet, so
    # a later change through the web console survives a restart. This (or the "set
    # password" form the console shows on first visit) is how a fresh server gets one —
    # the app deliberately has no admin surface: everything it could do, the console at
    # /admin does, and more.
    admin_password: str | None = None

    @model_validator(mode="after")
    def _no_key_over_plain_http(self) -> "Settings":
        # A key over unencrypted http would cross the network readable to anyone on it.
        # Loopback is exempt: that traffic never leaves the machine.
        key = self.vision_api_key.get_secret_value() if self.vision_api_key else ""
        url = urlsplit(self.vision_base_url)
        if key and url.scheme != "https" and not _is_loopback(url.hostname):
            raise ValueError(
                "OPENCOOK_VISION_API_KEY is set, but OPENCOOK_VISION_BASE_URL is not https:// — "
                "the key would be sent unencrypted. Use the endpoint's https:// address."
            )
        return self

    @property
    def db_path(self) -> Path:
        return self.data_dir / "opencook.db"

    @property
    def images_dir(self) -> Path:
        return self.data_dir / "images"

    @property
    def backups_dir(self) -> Path:
        return self.backup_dir or (self.data_dir / "backups")


def _is_loopback(host: str | None) -> bool:
    if host == "localhost":
        return True
    try:
        return ipaddress.ip_address(host or "").is_loopback
    except ValueError:
        return False


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    settings.images_dir.mkdir(parents=True, exist_ok=True)
    settings.backups_dir.mkdir(parents=True, exist_ok=True)
    return settings
