"""In-process async worker that drains the jobs table.

No external queue (Redis/Celery) — keeps self-hosting to a single process.
It claims pending jobs and runs recipe extraction in-process.
"""

import asyncio
import json
import logging
from pathlib import Path

from sqlalchemy import select

from app.config import get_settings
from app.db import SessionLocal
from app.extraction import RecipeExtractor, load_i18n
from app.models import Job, JobStatus, PromptOverride
from app.vision_client import VisionClient

logger = logging.getLogger(__name__)


async def _process_one(job_id: str) -> None:
    settings = get_settings()
    extractor = RecipeExtractor(VisionClient(), settings.images_dir)

    with SessionLocal() as session:
        job = session.get(Job, job_id)
        image_path = job.image_path if job else None
        language = job.language if job else None
        # The owner's instructions for the catalog this job will actually use, if any.
        override = session.get(PromptOverride, load_i18n(language).language)
        text_instructions = override.text_instructions if override else None
        box_instructions = override.box_instructions if override else None
    if not image_path:
        return

    def set_stage(stage: str) -> None:
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            if job is not None:
                job.stage = stage
                session.commit()

    try:
        recipes = await extractor.extract(
            Path(image_path), on_stage=set_stage, language=language,
            text_instructions=text_instructions, box_instructions=box_instructions,
        )
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            job.result_json = json.dumps(recipes, ensure_ascii=False)
            job.status = JobStatus.DONE
            job.stage = None
            session.commit()
        logger.info("Job %s done: %d recipe(s) extracted.", job_id, len(recipes))
    except Exception as exc:  # noqa: BLE001 - record the failure on the job
        logger.exception("Job %s failed", job_id)
        with SessionLocal() as session:
            job = session.get(Job, job_id)
            job.status = JobStatus.ERROR
            job.error = str(exc)
            session.commit()


async def worker_loop(stop_event: asyncio.Event) -> None:
    settings = get_settings()
    logger.info("Job worker started (poll interval %.1fs).", settings.worker_poll_interval)
    while not stop_event.is_set():
        try:
            with SessionLocal() as session:
                job = session.scalars(
                    select(Job).where(Job.status == JobStatus.PENDING).limit(1)
                ).first()
                if job is not None:
                    job.status = JobStatus.PROCESSING
                    session.commit()
                    job_id = job.id
                else:
                    job_id = None
            if job_id is not None:
                await _process_one(job_id)
        except Exception:  # noqa: BLE001 - worker must never die on a single bad job
            logger.exception("Worker iteration failed")
        try:
            await asyncio.wait_for(stop_event.wait(), timeout=settings.worker_poll_interval)
        except asyncio.TimeoutError:
            pass
    logger.info("Job worker stopped.")
