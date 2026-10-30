"""Admin API for the extraction prompts, and the worker applying a replacement.

Shares the (disposable) dev DB like the other API tests; each test clears the
overrides first so it doesn't depend on earlier state.
"""

import asyncio
import json

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app import worker
from app.config import get_settings
from app.db import SessionLocal, init_db
from app.extraction import load_i18n
from app.main import app
from app.models import Job, JobStatus, PromptOverride
from tests.test_admin import _auth, _set_password
from tests.test_extraction import FakeClient


@pytest.fixture
def admin_pw() -> str:
    init_db()
    with SessionLocal() as session:
        session.query(PromptOverride).delete()
        session.commit()
    pw = "geheim123"
    _set_password(pw)
    return pw


def test_prompts_are_password_gated(admin_pw: str) -> None:
    with TestClient(app) as client:
        assert client.get("/admin/prompts").status_code == 401
        assert client.patch("/admin/prompts/de", json={"text_instructions": "x"}).status_code == 401


def test_list_shows_defaults_and_format_for_every_language(admin_pw: str) -> None:
    with TestClient(app) as client:
        langs = {p["language"]: p for p in client.get("/admin/prompts", headers=_auth(admin_pw)).json()["languages"]}
    assert {"de", "en", "es", "fr"} <= langs.keys()
    de = langs["de"]
    assert de["text_default"] == load_i18n("de").text_instructions
    assert de["text_instructions"] is None and de["box_instructions"] is None
    assert '{"recipes"' in de["text_format"]


def test_replace_then_reset(admin_pw: str) -> None:
    with TestClient(app) as client:
        r = client.patch("/admin/prompts/de", headers=_auth(admin_pw),
                         json={"text_instructions": "  Eigene Regeln.  "})
        assert r.status_code == 200
        assert r.json()["text_instructions"] == "Eigene Regeln."
        assert r.json()["box_instructions"] is None  # untouched field stays default

        # null leaves a field alone; blank resets it — and with nothing custom left
        # the row goes away.
        r = client.patch("/admin/prompts/de", headers=_auth(admin_pw), json={"box_instructions": None})
        assert r.json()["text_instructions"] == "Eigene Regeln."
        r = client.patch("/admin/prompts/de", headers=_auth(admin_pw), json={"text_instructions": " "})
        assert r.json()["text_instructions"] is None
    with SessionLocal() as session:
        assert session.get(PromptOverride, "de") is None


def test_unknown_language_is_404(admin_pw: str) -> None:
    with TestClient(app) as client:
        r = client.patch("/admin/prompts/xx", headers=_auth(admin_pw), json={"text_instructions": "x"})
        assert r.status_code == 404


def test_worker_uses_the_replacement_for_the_jobs_language(admin_pw: str, tmp_path, monkeypatch) -> None:
    image = tmp_path / "page.jpg"
    Image.new("RGB", (200, 150), (210, 210, 210)).save(image)
    prompts: list[str] = []

    class RecordingClient(FakeClient):
        async def generate(self, prompt: str, image_bytes: bytes) -> str:
            prompts.append(prompt)
            return await super().generate(prompt, image_bytes)

    monkeypatch.setattr(worker, "VisionClient", RecordingClient)
    with SessionLocal() as session:
        session.add(PromptOverride(language="de", text_instructions="Eigene Regeln."))
        job = Job(status=JobStatus.PROCESSING, image_path=str(image), language="de")
        session.add(job)
        session.commit()
        job_id = job.id

    asyncio.run(worker._process_one(job_id))

    de = load_i18n("de")
    assert prompts[0] == f"Eigene Regeln.\n\n{de.text_format}"
    assert prompts[1] == de.box_prompt()  # no box replacement → built-in
    with SessionLocal() as session:
        done = session.get(Job, job_id)
        assert done.status == JobStatus.DONE
        recipe = json.loads(done.result_json)[0]
        assert recipe["name"] == "Test Rezept"
        for name in recipe["image"]:  # the crop landed in the shared dev images dir
            (get_settings().images_dir / name).unlink(missing_ok=True)
        session.delete(done)
        session.query(PromptOverride).delete()
        session.commit()


def test_language_usage_follows_the_households_setting() -> None:
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session

    from app.api.admin import _language_usage
    from app.db import Base
    from app.models import Household

    engine = create_engine("sqlite://")  # private, empty DB: no state from other tests
    Base.metadata.create_all(engine)
    with Session(engine) as session:
        assert _language_usage(session).in_use == {"en"}  # no client yet → the default
        session.add_all([
            Household(invite_code="A", name="Familie", settings_json=json.dumps({"content_language": "fr"})),
            Household(invite_code="B", name="Oma", settings_json=json.dumps({"content_language": "fr-CA"})),
            Job(status=JobStatus.DONE, image_path="x.jpg", language="de-AT"),
            PromptOverride(language="es", text_instructions="Reglas propias."),
        ])
        session.commit()
        usage = _language_usage(session)
        # Only the setting counts: neither the scan nor the custom prompt adds a language.
        assert usage.in_use == {"fr"}
        assert usage.by_household == {"fr": ["Familie", "Oma"]}

        # A household following its devices' system language has no setting; its
        # language only shows in the scans, which then count too — without a name.
        session.add(Household(invite_code="C", name="Test", settings_json="{}"))
        session.commit()
        usage = _language_usage(session)
        assert usage.in_use == {"fr", "de"}
        assert usage.from_scans == {"de"}
        assert usage.follow_device == ["Test"]
