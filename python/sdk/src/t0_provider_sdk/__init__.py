"""T-0 Network Provider SDK for Python.

This SDK provides:
- Cryptographic utilities for signing and verifying T-0 Network signatures
- Server-side ASGI middleware for signature verification
- Client-side signing transport for outgoing requests
- Generic, proto-agnostic handler/client registration

Usage (server):
    from t0_provider_sdk.provider import handler, new_asgi_app

    app = new_asgi_app(
        network_public_key,
        handler(ProviderServiceASGIApplication, my_service),
    )

Usage (client):
    from t0_provider_sdk.network import new_service_client

    client = new_service_client(
        private_key,
        NetworkServiceClient,
        base_url="https://api.t-0.network",
    )
"""

import sys
from pathlib import Path

# Add the api/ directory to sys.path so that generated protobuf code
# (which uses absolute imports like `from tzero.v1.common import ...`
# and `from buf.validate import ...`) can be resolved.
_api_dir = str(Path(__file__).parent / "api")
if _api_dir not in sys.path:
    sys.path.insert(0, _api_dir)
