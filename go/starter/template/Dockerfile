# Build stage
FROM golang:1.27-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./

RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-w -s" \
    -o /service \
    ./cmd

# Runtime stage
FROM gcr.io/distroless/base:nonroot

COPY --from=builder /service /service

WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["/service"]
