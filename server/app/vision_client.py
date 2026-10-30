"""Thin async client for the vision model, over the OpenAI chat-completions API.

That API is what nearly every model server speaks — Ollama (the default, at /v1),
llama.cpp, vLLM, LM Studio — and hosted services too. So one client covers them all,
and "local Ollama" is nothing but the default base URL.
"""

import asyncio
import base64
import ipaddress
import logging
from typing import Any
from urllib.parse import urlsplit

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

def _chat_text(body: Any) -> str:
    """Message text of a chat-completions response; a clear error if its shape is wrong."""
    try:
        content = body["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise ValueError("AI provider returned an invalid chat-completions response") from exc
    return content if isinstance(content, str) else ""


class VisionClient:
    def __init__(self, max_retries: int = 2, transport: httpx.AsyncBaseTransport | None = None) -> None:
        settings = get_settings()
        self._url = f"{settings.vision_base_url.rstrip('/')}/chat/completions"
        self._model = settings.vision_model
        key = settings.vision_api_key.get_secret_value() if settings.vision_api_key else ""
        self._headers = {"Authorization": f"Bearer {key}"} if key else {}
        self._params = settings.vision_params
        self._max_retries = max_retries
        self._transport = transport  # tests only

    async def generate(self, prompt: str, image_bytes: bytes) -> str:
        """Run the vision model on a single image and return the raw response text.

        Only the required fields plus the configured extra params are sent — no option
        works on every endpoint. Retries on 429 / 5xx / transport errors: a local model
        runner can crash mid-generation (e.g. flaky GPU/ROCm backends), and a reload +
        retry often succeeds.
        """
        image_b64 = base64.b64encode(image_bytes).decode("ascii")
        payload = {
            "model": self._model,
            "messages": [{
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}},
                ],
            }],
            "stream": False,
            **self._params,
        }
        last_error: Exception | None = None
        async with httpx.AsyncClient(timeout=httpx.Timeout(600.0), transport=self._transport) as client:
            for attempt in range(self._max_retries + 1):
                try:
                    response = await client.post(self._url, json=payload, headers=self._headers)
                    response.raise_for_status()
                    return _chat_text(response.json())
                except httpx.HTTPStatusError as exc:
                    # A 4xx won't change on retry; the body says which field the endpoint
                    # rejected — usually one of the configured params.
                    if exc.response.status_code != 429 and exc.response.status_code < 500:
                        logger.error("Vision endpoint rejected the request: %s", exc.response.text[:300])
                        raise
                    last_error = exc
                except httpx.TransportError as exc:
                    last_error = exc
                if attempt < self._max_retries:
                    logger.warning("Vision call failed (attempt %d), retrying: %s", attempt + 1, last_error)
                    await asyncio.sleep(2.0 * (attempt + 1))
        raise last_error  # type: ignore[misc]


async def warn_if_outside_home_network() -> None:
    """Log a warning when the vision endpoint is not on the home network.

    Scanned photos are sent there, so a public address (a hosted service) is worth
    one line in the log. Home = every address the host resolves to is non-global:
    private, loopback, link-local — and 100.64.0.0/10, so a Tailscale-style VPN
    doesn't trip it. Never blocks startup: an unresolvable host is only noted.
    """
    url = get_settings().vision_base_url
    host = urlsplit(url).hostname
    if not host:
        return
    try:
        infos = await asyncio.get_running_loop().getaddrinfo(host, None)
    except OSError:
        logger.info("Vision endpoint %s could not be resolved; scans will fail until it can", url)
        return
    # An IPv6 link-local result carries a zone ("fe80::1%eth0") that ip_address rejects.
    addresses = {ipaddress.ip_address(info[4][0].split("%")[0]) for info in infos}
    if any(addr.is_global for addr in addresses):
        logger.warning(
            "Vision endpoint %s is outside your home network — scanned photos will be sent there", url,
        )
