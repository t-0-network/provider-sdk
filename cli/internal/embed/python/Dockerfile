# Build stage
FROM python:3.13-slim AS builder

WORKDIR /app

RUN pip install --no-cache-dir uv

ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=0

COPY pyproject.toml uv.lock* ./
RUN uv sync --frozen --no-dev --no-install-project || uv sync --no-dev --no-install-project

COPY . .
RUN uv sync --frozen --no-dev --no-editable || uv sync --no-dev --no-editable

# Runtime stage — no uv, no source, just the venv
FROM python:3.13-slim

RUN groupadd --system --gid 999 app \
    && useradd --system --gid 999 --uid 999 --create-home app

COPY --from=builder --chown=app:app /app/.venv /app/.venv

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

USER app
WORKDIR /app

ENTRYPOINT ["python", "-m", "provider.main"]
