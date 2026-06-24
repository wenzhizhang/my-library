"""Add reading_plans table

Revision ID: 9b3c4d5e6f7a
Revises: 8a2b3c4d5e6f
Create Date: 2026-06-24 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '9b3c4d5e6f7a'
down_revision: Union[str, Sequence[str], None] = '8a2b3c4d5e6f'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table(
        'reading_plans',
        sa.Column('id', sa.Integer(), primary_key=True, index=True),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('intro', sa.String(length=1000), nullable=True),
        sa.Column('start_date', sa.Date(), nullable=True),
        sa.Column('end_date', sa.Date(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False),
    )
    op.create_table(
        'reading_plan_items',
        sa.Column('plan_id', sa.Integer(), sa.ForeignKey('reading_plans.id'), primary_key=True),
        sa.Column('book_id', sa.Integer(), sa.ForeignKey('books.id'), primary_key=True),
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_table('reading_plan_items')
    op.drop_table('reading_plans')
