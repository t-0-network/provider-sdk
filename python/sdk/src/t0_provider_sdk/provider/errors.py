"""Error types for signature verification."""

from t0_provider_sdk._messages import (
    BODY_TOO_LARGE,
    INVALID_HEADER_ENCODING,
    MISSING_HEADER,
    NETWORK_PUBLIC_KEY_NOT_SET,
    SIGNATURE_VERIFICATION_FAILED,
    TIMESTAMP_OUTSIDE_WINDOW,
    UNKNOWN_PUBLIC_KEY,
)
from t0_provider_sdk.common.headers import SIGNATURE_TIMESTAMP_HEADER


class NetworkPublicKeyRequiredError(ValueError):
    """new_asgi_app / new_wsgi_app got an empty network public key."""

    def __init__(self) -> None:
        super().__init__(NETWORK_PUBLIC_KEY_NOT_SET)


class SignatureVerificationError(Exception):
    """Base class for all signature verification errors."""


class MissingRequiredHeaderError(SignatureVerificationError):
    """A required signature header is missing."""

    def __init__(self, header_name: str) -> None:
        super().__init__(MISSING_HEADER.format(header=header_name))
        self.header_name = header_name


class InvalidHeaderEncodingError(SignatureVerificationError):
    """A signature header has invalid encoding."""

    def __init__(self, header_name: str) -> None:
        super().__init__(INVALID_HEADER_ENCODING.format(header=header_name))
        self.header_name = header_name


class InvalidTimestampError(InvalidHeaderEncodingError):
    """The X-Signature-Timestamp header is not a decimal number, or does not fit 64 bits.

    An InvalidHeaderEncodingError of that header, with a message of its own: TIMESTAMP_NOT_DECIMAL
    or TIMESTAMP_OUT_OF_RANGE.
    """

    def __init__(self, message: str) -> None:
        SignatureVerificationError.__init__(self, message)
        self.header_name = SIGNATURE_TIMESTAMP_HEADER


class TimestampOutOfRangeError(SignatureVerificationError):
    """Request timestamp is outside the allowed time window."""

    def __init__(self) -> None:
        super().__init__(TIMESTAMP_OUTSIDE_WINDOW)


class UnknownPublicKeyError(SignatureVerificationError):
    """The signer's public key doesn't match the expected network key."""

    def __init__(self) -> None:
        super().__init__(UNKNOWN_PUBLIC_KEY)


class SignatureFailedError(SignatureVerificationError):
    """The signature verification failed."""

    def __init__(self) -> None:
        super().__init__(SIGNATURE_VERIFICATION_FAILED)


class BodyTooLargeError(SignatureVerificationError):
    """Request body exceeds the maximum allowed size."""

    def __init__(self, max_size: int) -> None:
        super().__init__(BODY_TOO_LARGE.format(limit=max_size))
        self.max_size = max_size
