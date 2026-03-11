# Build stage
FROM python:3.13-slim AS builder

WORKDIR /app

# Install uv for fast dependency management
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY . .

# Runtime stage
FROM python:3.13-slim

WORKDIR /app

COPY --from=builder /app /app

ENTRYPOINT ["uv", "run", "python", "-m", "provider.main"]
