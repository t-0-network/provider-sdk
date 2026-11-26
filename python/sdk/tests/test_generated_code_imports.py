"""No SDK package except kyc_sharing imports the generated code in api/. A caller's own stubs of the
same protos would shadow it on sys.path, or clash with it in the default descriptor pool."""

from __future__ import annotations

import pkgutil
import subprocess
import sys

import pytest
import t0_provider_sdk

PACKAGES = ["t0_provider_sdk"] + [
    f"t0_provider_sdk.{module.name}"
    for module in pkgutil.iter_modules(t0_provider_sdk.__path__)
    if module.name not in ("api", "kyc_sharing")
]

# Prints the modules loaded from api/. Runs in a new interpreter: this session has imported them.
_LIST_GENERATED = """
import sys
from pathlib import Path
api = Path(sys.modules["t0_provider_sdk"].__file__).parent / "api"
print(*sorted(n for n, m in sys.modules.items() if getattr(m, "__file__", None) and Path(m.__file__).is_relative_to(api)))
"""


@pytest.mark.parametrize("package", PACKAGES)
def test_package_does_not_import_generated_code(package: str) -> None:
    result = subprocess.run(
        [sys.executable, "-c", f"import {package}\n{_LIST_GENERATED}"], capture_output=True, text=True, check=True
    )
    assert result.stdout.strip() == ""
