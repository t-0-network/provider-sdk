"""Config validation: keys, port, endpoint, and the missing-.env notice. Does not start a server."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from provider.config import ConfigurationError, load_config  # noqa: E402

PRIVATE_KEY = "ab" * 32
NETWORK_PUBLIC_KEY = "cd" * 32
SANDBOX = "https://api-sandbox.t-0.network"
PRIVATE_KEY_HELP = (
    ".env is read from the working directory, and we looked in {env_path}. Run the app from the "
    "directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. Only a project "
    "with no .env at all starts one from .env.example — an existing .env holds the key generated "
    "for you, and its private half is not recoverable."
)
NETWORK_HELP = "Ask the t-0 team for the network public key and put it in .env."
PORT_HELP = "Set PORT to an integer between 1 and 65535, or leave it unset for 8080."

_KEYS = {"PROVIDER_PRIVATE_KEY": PRIVATE_KEY, "NETWORK_PUBLIC_KEY": NETWORK_PUBLIC_KEY}


# ``error`` is the ConfigurationError message. ``help`` is its help line (``"private"`` fills in
# the path). ``config`` is what a successful load returns. ``missing_env`` expects the notice.
CASES = [
    {
        "id": "keys-trimmed",
        "env": {"PROVIDER_PRIVATE_KEY": f"  {PRIVATE_KEY}\n", "NETWORK_PUBLIC_KEY": f"\n{NETWORK_PUBLIC_KEY}  "},
        "config": {"provider_private_key": PRIVATE_KEY, "network_public_key": NETWORK_PUBLIC_KEY},
    },
    {
        "id": "blank-private-key",
        "env": {"PROVIDER_PRIVATE_KEY": "  \n", "NETWORK_PUBLIC_KEY": NETWORK_PUBLIC_KEY},
        "error": "PROVIDER_PRIVATE_KEY is not set",
        "help": "private",
    },
    {
        "id": "blank-network-key",
        "env": {"PROVIDER_PRIVATE_KEY": PRIVATE_KEY, "NETWORK_PUBLIC_KEY": "   "},
        "error": "NETWORK_PUBLIC_KEY is not set",
        "help": NETWORK_HELP,
    },
    {"id": "port-blank", "env": {**_KEYS, "PORT": ""}, "config": {"port": 8080}},
    {"id": "port-whitespace", "env": {**_KEYS, "PORT": "   "}, "config": {"port": 8080}},
    {"id": "port-8080", "env": {**_KEYS, "PORT": "8080"}, "config": {"port": 8080}},
    {"id": "port-padded", "env": {**_KEYS, "PORT": " 8080 "}, "config": {"port": 8080}},
    {
        "id": "port-zero",
        "env": {**_KEYS, "PORT": "0"},
        "error": "PORT is not a valid port number: 0",
        "help": PORT_HELP,
    },
    {
        "id": "port-http",
        "env": {**_KEYS, "PORT": "http"},
        "error": "PORT is not a valid port number: http",
        "help": PORT_HELP,
    },
    {
        "id": "port-out-of-range",
        "env": {**_KEYS, "PORT": "65536"},
        "error": "PORT is not a valid port number: 65536",
        "help": PORT_HELP,
    },
    {"id": "endpoint-blank", "env": {**_KEYS, "TZERO_ENDPOINT": ""}, "config": {"tzero_endpoint": SANDBOX}},
    {"id": "endpoint-whitespace", "env": {**_KEYS, "TZERO_ENDPOINT": "   "}, "config": {"tzero_endpoint": SANDBOX}},
    {"id": "endpoint-unset", "env": dict(_KEYS), "config": {"tzero_endpoint": SANDBOX}},
    {
        "id": "missing-env-notice",
        "env": dict(_KEYS),
        "missing_env": True,
        "config": {"provider_private_key": PRIVATE_KEY, "tzero_endpoint": SANDBOX, "port": 8080},
    },
    {
        "id": "environment-wins",
        "dotenv": (
            "PROVIDER_PRIVATE_KEY=from-file\nNETWORK_PUBLIC_KEY=from-file\n"
            "PORT=9000\nTZERO_ENDPOINT=https://from-file.example\n"
        ),
        "env": {**_KEYS, "PORT": "9100", "TZERO_ENDPOINT": "https://from-env.example"},
        "config": {
            "provider_private_key": PRIVATE_KEY,
            "network_public_key": NETWORK_PUBLIC_KEY,
            "port": 9100,
            "tzero_endpoint": "https://from-env.example",
        },
    },
]


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch, tmp_path):
    for key in (
        "PROVIDER_PRIVATE_KEY",
        "NETWORK_PUBLIC_KEY",
        "TZERO_ENDPOINT",
        "PORT",
        "QUOTE_PUBLISHING_INTERVAL",
        "PYTHON_DOTENV_DISABLED",
    ):
        # setenv first so monkeypatch removes the key at teardown, including a value
        # load_dotenv() wrote into os.environ during the test.
        monkeypatch.setenv(key, "")
        monkeypatch.delenv(key)
    monkeypatch.chdir(tmp_path)


@pytest.mark.parametrize("case", CASES, ids=[case["id"] for case in CASES])
def test_load_config(case, monkeypatch, tmp_path, capsys):
    if not case.get("missing_env"):
        (tmp_path / ".env").write_text(case.get("dotenv", ""))
    for key, value in case["env"].items():
        monkeypatch.setenv(key, value)

    if "error" in case:
        with pytest.raises(ConfigurationError) as raised:
            load_config()
        assert str(raised.value) == case["error"]
        help_text = case["help"]
        if help_text == "private":
            help_text = PRIVATE_KEY_HELP.format(env_path=(tmp_path / ".env").resolve())
        assert raised.value.help_text == help_text
        return

    config = load_config()
    for field, expected in case["config"].items():
        assert getattr(config, field) == expected
    if case.get("missing_env"):
        notice = f"No .env at {(tmp_path / '.env').resolve()} — taking configuration from the environment instead\n"
        assert capsys.readouterr().err == notice
    else:
        assert capsys.readouterr().err == ""
