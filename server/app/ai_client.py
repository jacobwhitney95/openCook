"""OpenAI-compatible client for vision-capable model APIs."""

import asyncio
import base64
import logging
from typing import Protocol

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)


class VisionModelClient(Protocol):
    async def generate(self, prompt: str, image_bytes: bytes) -> str: ...


class OpenAICompatibleClient:
    def __init__(
        self,
        max_retries: int = 2,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        settings = get_settings()
        self._base_url = settings.ai_base_url.rstrip("/")
        self._api_key = (
            settings.ai_api_key.get_secret_value() if settings.ai_api_key is not None else ""
        )
        self._model = settings.ai_model
        self._max_retries = max_retries
        self._transport = transport
        if max_retries < 0:
            raise ValueError("max_retries must be non-negative")

    async def generate(self, prompt: str, image_bytes: bytes) -> str:
        """Run a vision model on one image and return its response text."""
        image_b64 = base64.b64encode(image_bytes).decode("ascii")
        payload = {
            "model": self._model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{image_b64}",
                            },
                        },
                    ],
                }
            ],
            "stream": False,
        }
        headers = (
            {"Authorization": f"Bearer {self._api_key}"} if self._api_key else {}
        )
        last_error: httpx.HTTPStatusError | httpx.TransportError | None = None
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(600.0), transport=self._transport
        ) as client:
            for attempt in range(self._max_retries + 1):
                try:
                    response = await client.post(
                        f"{self._base_url}/chat/completions",
                        headers=headers,
                        json=payload,
                    )
                    response.raise_for_status()
                    try:
                        content = response.json()["choices"][0]["message"]["content"]
                    except (ValueError, KeyError, IndexError, TypeError) as exc:
                        raise ValueError(
                            "AI provider returned an invalid chat-completions response"
                        ) from exc
                    if not isinstance(content, str) or not content.strip():
                        raise ValueError(
                            "AI provider returned empty or non-text chat-completions content"
                        )
                    return content
                except (httpx.HTTPStatusError, httpx.TransportError) as exc:
                    last_error = exc
                    if attempt < self._max_retries:
                        logger.warning(
                            "AI provider call failed (attempt %d), retrying: %s",
                            attempt + 1,
                            exc,
                        )
                        await asyncio.sleep(2.0 * (attempt + 1))
        if last_error is not None:
            raise last_error
        raise RuntimeError("AI provider request ended without a response or error")
