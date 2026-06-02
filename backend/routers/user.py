import uuid as _uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_auth_db
from models.user import User
from schemas.user import UserRegister, UserLogin, TokenResponse, UserInfo
from auth import hash_password, verify_password, create_access_token, get_current_user_id

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _generate_uuid() -> str:
    return _uuid.uuid4().hex


@router.post("/register", response_model=TokenResponse)
def register(data: UserRegister, db: Session = Depends(get_auth_db)):
    existing = db.query(User).filter(User.username == data.username).first()
    if existing:
        raise HTTPException(status_code=400, detail="Username already taken")

    user_uuid = _generate_uuid()
    user = User(
        username=data.username,
        password_hash=hash_password(data.password),
        uuid=user_uuid,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    token = create_access_token({"sub": str(user.id), "uuid": user.uuid})
    return TokenResponse(
        access_token=token,
        user_id=user.id,
        username=user.username,
        uuid=user.uuid,
    )


@router.post("/login", response_model=TokenResponse)
def login(data: UserLogin, db: Session = Depends(get_auth_db)):
    user = db.query(User).filter(User.username == data.username).first()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid username or password")

    token = create_access_token({"sub": str(user.id), "uuid": user.uuid})
    return TokenResponse(
        access_token=token,
        user_id=user.id,
        username=user.username,
        uuid=user.uuid,
    )


@router.get("/db-info")
def db_info(user_id: int = Depends(get_current_user_id), db: Session = Depends(get_auth_db)):
    """Diagnostic: show which database this request is using."""
    from database import DATA_DIR, init_user_db
    import os
    user = db.query(User).filter(User.id == user_id).first() if user_id else None
    if user and user.uuid:
        user_db = init_user_db(user.uuid)
        exists = os.path.exists(user_db)
        return {"user_id": user_id, "uuid": user.uuid, "db": user_db, "exists": exists, "type": "user"}
    return {"user_id": None, "db": os.path.join(DATA_DIR, "demo.db"), "exists": True, "type": "demo"}


@router.get("/me", response_model=UserInfo)
def me(user_id: int = Depends(get_current_user_id), db: Session = Depends(get_auth_db)):
    if user_id is None:
        raise HTTPException(status_code=401)
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404)
    return user
