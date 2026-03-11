"""Entry point for the T-0 Network provider.

Go equivalent: cmd/main.go → main()

Initializes the network client, starts the provider server,
and manages quote publishing and retrieval.

--- WSGI alternative ---
To use a synchronous WSGI server (e.g. gunicorn) instead of ASGI/uvicorn,
swap the following imports and use the sync variants:

    # Replace async imports:
    #   from t0_provider_sdk.api.tzero.v1.payment.provider_connect import ProviderServiceASGIApplication
    #   from t0_provider_sdk.network.client import new_service_client
    #   from t0_provider_sdk.provider.handler import handler, new_asgi_app
    # With sync imports:
    #   from t0_provider_sdk.api.tzero.v1.payment.provider_connect import ProviderServiceWSGIApplication
    #   from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClientSync
    #   from t0_provider_sdk.network.client import new_service_client_sync
    #   from t0_provider_sdk.provider.handler import handler_sync, new_wsgi_app

    # Use the sync handler implementation:
    #   from provider.handler.payment_sync import ProviderServiceSyncImplementation

    # Build the WSGI app:
    #   network_client = new_service_client_sync(private_key, NetworkServiceClientSync, base_url=...)
    #   service = ProviderServiceSyncImplementation(network_client)
    #   app = new_wsgi_app(network_public_key, handler_sync(ProviderServiceWSGIApplication, service))

    # Run with gunicorn (from command line):
    #   gunicorn provider.main:wsgi_app --bind 0.0.0.0:8080
"""

from __future__ import annotations

import asyncio
import logging
import signal

import uvicorn
from t0_provider_sdk.api.tzero.v1.payment.network_connect import NetworkServiceClient
from t0_provider_sdk.api.tzero.v1.payment.provider_connect import ProviderServiceASGIApplication
from t0_provider_sdk.network.client import new_service_client
from t0_provider_sdk.provider.handler import handler, new_asgi_app

from provider.config import Config, load_config
from provider.get_quote import get_quote
from provider.handler.payment import ProviderServiceImplementation
from provider.publish_quotes import publish_quotes

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)


def init_network_client(config: Config) -> NetworkServiceClient:
    """Create a network service client with signing transport.

    Go equivalent: initNetworkClient()
    """
    return new_service_client(
        config.provider_private_key,
        NetworkServiceClient,
        base_url=config.tzero_endpoint,
    )


def create_provider_app(config: Config, network_client: NetworkServiceClient):
    """Create the provider ASGI application.

    Go equivalent: startProviderServer()
    """
    service = ProviderServiceImplementation(network_client)
    return new_asgi_app(
        config.network_public_key,
        handler(ProviderServiceASGIApplication, service),
    )


async def main() -> None:
    config = load_config()

    network_client = init_network_client(config)

    app = create_provider_app(config, network_client)

    # Step 1.1 is done. You successfully initialised starter template
    logger.info("Step 1.1: Provider server initialized on :%d", config.port)

    # TODO: Step 1.2 Share the generated public key from .env with t-0 team

    # TODO: Step 1.3 Replace publish_quotes with your own quote publishing logic
    shutdown_event = asyncio.Event()

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, shutdown_event.set)

    publish_task = asyncio.create_task(publish_quotes(network_client, shutdown_event))

    # TODO: Step 1.4 Verify that quotes for target currency are successfully received
    quote_task = asyncio.create_task(get_quote(network_client))

    # Run ASGI server
    server_config = uvicorn.Config(app, host="0.0.0.0", port=config.port, log_level="info")
    server = uvicorn.Server(server_config)

    # TODO: Step 2.2 Deploy your integration and provide t-0 team with the base URL
    # TODO: Step 2.3 Test payment submission
    # TODO: Step 2.5 Ask t-0 team to submit a payment to test your payOut endpoint

    await server.serve()

    # Clean up background tasks
    shutdown_event.set()
    publish_task.cancel()
    quote_task.cancel()


if __name__ == "__main__":
    asyncio.run(main())
