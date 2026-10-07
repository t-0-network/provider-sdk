"""Default options for network client connections."""

from enum import Enum

DEFAULT_BASE_URL = "https://api.t-0.network"
# Timeouts are in seconds.
DEFAULT_TIMEOUT = 15.0
DEFAULT_STREAM_TIMEOUT = 300.0
# The largest timeout and stream timeout a client accepts: 2147483647 ms.
MAX_TIMEOUT = 2_147_483_647 / 1000


class WireFormat(Enum):
    """How request and response messages are encoded."""

    BINARY = "binary"
    JSON = "json"


class Protocol(Enum):
    """The RPC protocol of the client."""

    CONNECT = "connect"
    GRPC = "grpc"
