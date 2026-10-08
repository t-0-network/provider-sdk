"""The SDK version a provider server reports."""

from t0_provider_sdk._version import __version__


def _reported_version(version: str | None) -> str:
    """The version a server reports, in its health headers and its logs: the override when it is
    not blank, else this SDK's."""
    return version if version and version.strip() else __version__
