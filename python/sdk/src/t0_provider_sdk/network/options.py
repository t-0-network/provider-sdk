"""Default options for network client connections."""

from enum import Enum

DEFAULT_BASE_URL = "https://api.t-0.network"
DEFAULT_TIMEOUT = 15.0
DEFAULT_STREAM_TIMEOUT = 300.0


class WireFormat(Enum):
    """How request and response messages are encoded."""

    BINARY = "binary"
    JSON = "json"


class Protocol(Enum):
    """The RPC protocol of the client."""

    CONNECT = "connect"
    GRPC = "grpc"
