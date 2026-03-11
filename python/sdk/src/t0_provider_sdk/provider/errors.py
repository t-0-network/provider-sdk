"""Error types for signature verification.

Go equivalent: provider/verify_signature.go error sentinel values.
"""


class SignatureVerificationError(Exception):
    """Base class for all signature verification errors."""


class MissingRequiredHeaderError(SignatureVerificationError):
    """A required signature header is missing."""

    def __init__(self, header_name: str) -> None:
        super().__init__(f"missing required header: {header_name}")
        self.header_name = header_name


class InvalidHeaderEncodingError(SignatureVerificationError):
    """A signature header has invalid encoding."""

    def __init__(self, header_name: str) -> None:
        super().__init__(f"invalid header encoding: {header_name}")
        self.header_name = header_name


class TimestampOutOfRangeError(SignatureVerificationError):
    """Request timestamp is outside the allowed time window."""

    def __init__(self) -> None:
        super().__init__("timestamp is outside the allowed time window")


class UnknownPublicKeyError(SignatureVerificationError):
    """The signer's public key doesn't match the expected network key."""

    def __init__(self) -> None:
        super().__init__("unknown public key")


class SignatureFailedError(SignatureVerificationError):
    """The signature verification failed."""

    def __init__(self) -> None:
        super().__init__("signature verification failed")


class BodyTooLargeError(SignatureVerificationError):
    """Request body exceeds the maximum allowed size."""

    def __init__(self, max_size: int) -> None:
        super().__init__(f"max payload size of {max_size} bytes exceeded")
        self.max_size = max_size
