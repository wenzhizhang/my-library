"""Add book_collections table

Revision ID: 8a2b3c4d5e6f
Revises: 7f12e6cff8e1
Create Date: 2026-06-13 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '8a2b3c4d5e6f'
down_revision: Union[str, Sequence[str], None] = '7f12e6cff8e1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table(
        'book_collections',
        sa.Column('id', sa.Integer(), primary_key=True, index=True),
        sa.Column('name', sa.String(length=255), nullable=False),
        sa.Column('intro', sa.String(length=1000), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False),
    )
    op.create_table(
        'book_collection_items',
        sa.Column('collection_id', sa.Integer(), sa.ForeignKey('book_collections.id'), primary_key=True),
        sa.Column('book_id', sa.Integer(), sa.ForeignKey('books.id'), primary_key=True),
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_table('book_collection_items')
    op.drop_table('book_collections')
