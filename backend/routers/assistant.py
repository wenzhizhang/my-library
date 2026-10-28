"""Assistant book actions: metadata drafts plus explicit, confirmed writes."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from auth import require_user_id
from database import get_db
from schemas.assistant import AssistantActionRequest, AssistantCommitRequest
from services.assistant_actions import commit_draft, handle_action

router = APIRouter(prefix="/api/assistant", tags=["assistant"])


@router.post("/actions")
async def run_action(
    payload: AssistantActionRequest,
    db: Session = Depends(get_db),
    user_id: int = Depends(require_user_id),
):
    return await handle_action(
        db,
        message=payload.message,
        draft=payload.draft,
        focus_book_id=payload.focus_book_id,
        action=payload.action,
        page_context=payload.page_context.model_dump() if payload.page_context else None,
    )


@router.post("/commit")
def commit(
    payload: AssistantCommitRequest,
    db: Session = Depends(get_db),
    user_id: int = Depends(require_user_id),
):
    return commit_draft(db, payload.draft, user_id, payload.duplicate_choice)
