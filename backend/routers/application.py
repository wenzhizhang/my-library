from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from models import Application
from schemas.application import ApplicationCreation, ApplicationUpdate, ApplicationResponse
from database import get_db

router = APIRouter(prefix="/api/applications", tags=["applications"])


@router.post("/", response_model=ApplicationResponse)
def create_application(application: ApplicationCreation, db: Session = Depends(get_db)):
    """注册一个新应用"""
    db_app = Application(**application.model_dump())
    db.add(db_app)
    db.commit()
    db.refresh(db_app)
    return db_app


@router.get("/")
def read_applications(
    page: int = 1,
    limit: int = 20,
    sort_by: str = 'sort_order',
    db: Session = Depends(get_db),
):
    """获取应用列表（分页）"""
    offset = (page - 1) * limit
    query = db.query(Application)
    if sort_by == 'sort_order':
        query = query.order_by(Application.sort_order, Application.name)
    elif sort_by == 'name':
        query = query.order_by(Application.name)
    elif sort_by == 'created_at':
        query = query.order_by(Application.created_at.desc())

    total = db.query(Application).count()
    total_pages = max((total + limit - 1) // limit, 1)
    applications = query.offset(offset).limit(limit).all()

    return {
        "applications": [
            {
                "id": app.id,
                "name": app.name,
                "description": app.description,
                "url": app.url,
                "icon_url": app.icon_url,
                "sort_order": app.sort_order,
                "created_at": app.created_at,
                "updated_at": app.updated_at,
            }
            for app in applications
        ],
        "total": total,
        "total_pages": total_pages,
        "page": page,
        "limit": limit,
    }


@router.get("/{application_id}", response_model=ApplicationResponse)
def read_application(application_id: int, db: Session = Depends(get_db)):
    """获取单个应用详情"""
    app = db.query(Application).filter(Application.id == application_id).first()
    if app is None:
        raise HTTPException(status_code=404, detail="Application not found")
    return app


@router.put("/{application_id}", response_model=ApplicationResponse)
def update_application(
    application_id: int,
    application_update: ApplicationUpdate,
    db: Session = Depends(get_db),
):
    """更新应用信息"""
    app = db.query(Application).filter(Application.id == application_id).first()
    if app is None:
        raise HTTPException(status_code=404, detail="Application not found")
    for key, value in application_update.model_dump(exclude_unset=True).items():
        setattr(app, key, value)
    db.commit()
    db.refresh(app)
    return app


@router.delete("/{application_id}")
def delete_application(application_id: int, db: Session = Depends(get_db)):
    """删除应用"""
    app = db.query(Application).filter(Application.id == application_id).first()
    if app is None:
        raise HTTPException(status_code=404, detail="Application not found")
    db.delete(app)
    db.commit()
    return {"message": "Application deleted"}
