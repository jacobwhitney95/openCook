import asyncio
import json
import logging

import httpx
import pytest

from app.config import get_settings
from app.vision_client import VisionClient, warn_if_outside_home_network


@pytest.fixture
def settings(monkeypatch, tmp_path):
    """Fresh settings from env for each test (get_settings is cached)."""
    monkeypatch.setenv("OPENCOOK_DATA_DIR", str(tmp_path))
    for name in ("BASE_URL", "MODEL", "API_KEY", "PARAMS"):
        monkeypatch.delenv(f"OPENCOOK_VISION_{name}", raising=False)
    get_settings.cache_clear()
    yield monkeypatch
    get_settings.cache_clear()


def _reply(text: str) -> httpx.Response:
    return httpx.Response(200, json={"choices": [{"message": {"content": text}}]})


def _run(client: VisionClient) -> str:
    return asyncio.run(client.generate("Prompt", b"\xff\xd8jpeg"))


def test_request_defaults_to_local_ollama_without_key(settings):
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return _reply('{"recipes": []}')

    assert _run(VisionClient(transport=httpx.MockTransport(handler))) == '{"recipes": []}'
    request = seen[0]
    assert str(request.url) == "http://localhost:11434/v1/chat/completions"
    assert "authorization" not in request.headers
    body = json.loads(request.content)
    assert body["model"] == "qwen2.5vl:7b"
    # Only the default params, tuned for Ollama: near-greedy like the Modelfile (its /v1
    # would otherwise sample at 1.0) and JSON mode.
    assert set(body) == {"model", "messages", "stream", "temperature", "response_format"}
    assert body["temperature"] == 0.0001
    assert body["response_format"] == {"type": "json_object"}
    text, image = body["messages"][0]["content"]
    assert text == {"type": "text", "text": "Prompt"}
    assert image["image_url"]["url"].startswith("data:image/jpeg;base64,")


def test_configured_endpoint_key_and_params(settings):
    settings.setenv("OPENCOOK_VISION_BASE_URL", "https://example.org/api/v1/")
    settings.setenv("OPENCOOK_VISION_MODEL", "some/vision-model")
    settings.setenv("OPENCOOK_VISION_API_KEY", "secret")
    settings.setenv("OPENCOOK_VISION_PARAMS", '{"max_tokens": 4000, "response_format": {"type": "json_object"}}')
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return _reply("{}")

    _run(VisionClient(transport=httpx.MockTransport(handler)))
    request = seen[0]
    assert str(request.url) == "https://example.org/api/v1/chat/completions"
    assert request.headers["authorization"] == "Bearer secret"
    body = json.loads(request.content)
    assert body["model"] == "some/vision-model"
    # The configured params replace the default — no temperature unless listed.
    assert body["max_tokens"] == 4000
    assert body["response_format"] == {"type": "json_object"}
    assert "temperature" not in body


def test_empty_params_send_required_fields_only(settings):
    # e.g. for an endpoint that rejects a temperature (OpenAI's reasoning models).
    settings.setenv("OPENCOOK_VISION_PARAMS", "{}")
    bodies = []

    def handler(request: httpx.Request) -> httpx.Response:
        bodies.append(json.loads(request.content))
        return _reply("{}")

    _run(VisionClient(transport=httpx.MockTransport(handler)))
    assert set(bodies[0]) == {"model", "messages", "stream"}


def test_empty_key_sends_no_header(settings):
    # docker-compose passes an unset key through as an empty string.
    settings.setenv("OPENCOOK_VISION_API_KEY", "")
    seen = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return _reply("{}")

    _run(VisionClient(transport=httpx.MockTransport(handler)))
    assert "authorization" not in seen[0].headers


def test_server_errors_are_retried(settings, monkeypatch):
    monkeypatch.setattr("app.vision_client.asyncio.sleep", _no_sleep)
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(500) if len(calls) < 3 else _reply("{}")

    assert _run(VisionClient(transport=httpx.MockTransport(handler))) == "{}"
    assert len(calls) == 3


def test_client_errors_are_not_retried(settings):
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(401)

    with pytest.raises(httpx.HTTPStatusError):
        _run(VisionClient(transport=httpx.MockTransport(handler)))
    assert len(calls) == 1


async def _no_sleep(_seconds: float) -> None:
    return None


@pytest.mark.parametrize(
    ("url", "warns"),
    [
        ("http://localhost:11434/v1", False),
        ("http://192.168.1.20:8080/v1", False),
        ("http://100.101.102.103:11434/v1", False),  # Tailscale / CGNAT range
        ("http://[fd00::1]:8080/v1", False),
        ("https://8.8.8.8/api/v1", True),
    ],
)
def test_warns_only_outside_home_network(settings, caplog, url, warns):
    settings.setenv("OPENCOOK_VISION_BASE_URL", url)
    with caplog.at_level(logging.WARNING, logger="app.vision_client"):
        asyncio.run(warn_if_outside_home_network())
    assert ("outside your home network" in caplog.text) is warns



@pytest.mark.parametrize(
    ("url", "allowed"),
    [
        ("https://openrouter.ai/api/v1", True),
        ("https://192.168.1.20:8443/v1", True),
        ("http://localhost:8080/v1", True),  # loopback never leaves the machine
        ("http://127.0.0.1:8080/v1", True),
        ("http://[::1]:8080/v1", True),
        ("http://192.168.1.20:8080/v1", False),
        ("http://api.example.org/v1", False),
    ],
)
def test_api_key_requires_https_off_loopback(settings, url, allowed):
    settings.setenv("OPENCOOK_VISION_BASE_URL", url)
    settings.setenv("OPENCOOK_VISION_API_KEY", "secret")
    if allowed:
        get_settings()
    else:
        with pytest.raises(ValueError, match="not https"):
            get_settings()


def test_plain_http_without_key_is_fine(settings):
    settings.setenv("OPENCOOK_VISION_BASE_URL", "http://192.168.1.20:11434/v1")
    assert get_settings().vision_base_url.startswith("http://")


def test_invalid_chat_completion_response_is_a_clear_error(settings):
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": []})

    with pytest.raises(ValueError, match="invalid chat-completions response"):
        _run(VisionClient(max_retries=0, transport=httpx.MockTransport(handler)))
