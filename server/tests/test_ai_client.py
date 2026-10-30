import asyncio
import base64
import json

import httpx
import pytest
from pydantic import SecretStr

from app.ai_client import OpenAICompatibleClient
from app.config import Settings


def test_client_sends_openai_compatible_vision_request(monkeypatch):
    captured = {}

    async def handle(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers.get("Authorization")
        captured["payload"] = json.loads(request.content)
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": '{"recipes": []}'}}]},
        )

    settings = Settings(
        _env_file=None,
        ai_base_url="http://ollama:11434/v1/",
        ai_api_key=SecretStr("ollama"),
        ai_model="gemma4:12b",
    )
    monkeypatch.setattr("app.ai_client.get_settings", lambda: settings)
    client = OpenAICompatibleClient(
        max_retries=0,
        transport=httpx.MockTransport(handle),
    )

    result = asyncio.run(client.generate("Extract recipes", b"photo-bytes"))

    assert result == '{"recipes": []}'
    assert captured["url"] == "http://ollama:11434/v1/chat/completions"
    assert captured["authorization"] == "Bearer ollama"
    assert captured["payload"]["model"] == "gemma4:12b"
    assert captured["payload"]["stream"] is False
    assert captured["payload"]["messages"] == [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "Extract recipes"},
                {
                    "type": "image_url",
                    "image_url": {
                        "url": "data:image/jpeg;base64,"
                        + base64.b64encode(b"photo-bytes").decode("ascii")
                    },
                },
            ],
        }
    ]


def test_client_omits_authorization_when_key_is_empty(monkeypatch):
    captured = {}

    async def handle(request: httpx.Request) -> httpx.Response:
        captured["authorization"] = request.headers.get("Authorization")
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": "ok"}}]},
        )

    settings = Settings(_env_file=None, ai_api_key=SecretStr(""))
    monkeypatch.setattr("app.ai_client.get_settings", lambda: settings)

    result = asyncio.run(
        OpenAICompatibleClient(
            max_retries=0,
            transport=httpx.MockTransport(handle),
        ).generate("prompt", b"image")
    )

    assert result == "ok"
    assert captured["authorization"] is None


def test_client_rejects_invalid_chat_completion_response(monkeypatch):
    async def handle(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": []})

    monkeypatch.setattr(
        "app.ai_client.get_settings",
        lambda: Settings(_env_file=None),
    )
    client = OpenAICompatibleClient(
        max_retries=0,
        transport=httpx.MockTransport(handle),
    )

    with pytest.raises(ValueError, match="invalid chat-completions response"):
        asyncio.run(client.generate("prompt", b"image"))
