"""CLI entry point for T-0 Provider project initialization.

Invocation: uvx t0-provider-starter my_provider
Go equivalent: provider-starter-go/main.go
"""

from __future__ import annotations

import os
import shutil
from importlib import resources
from pathlib import Path

import click
from t0_provider_starter.keygen import generate_keypair

TEMPLATE_PLACEHOLDER = "{{PROJECT_NAME}}"


@click.command()
@click.argument("project_name")
@click.option("-d", "--directory", default=None, help="Target directory (defaults to ./<project_name>)")
def main(project_name: str, directory: str | None) -> None:
    """Initialize a new T-0 Network provider project."""
    target_dir = Path(directory) if directory else Path(f"./{project_name}")

    # Validate target directory
    if target_dir.exists() and any(target_dir.iterdir()):
        click.echo(f"Error: target directory {target_dir} exists and is non-empty", err=True)
        raise SystemExit(1)

    # Generate keypair
    private_key, public_key = generate_keypair()

    # Copy template
    _copy_template(target_dir, project_name)

    # Write .env from .env.example
    _write_env(target_dir, private_key, public_key)

    # Print success
    click.echo(f"Initialized {project_name} in {target_dir}")
    click.echo(f"Provider public key: {public_key}")
    click.echo()
    click.echo("Next steps:")
    click.echo(f"  cd {target_dir}")
    click.echo("  uv sync")
    click.echo("  # Share the generated public key from .env with the t-0 team")
    click.echo("  uv run python -m provider.main")


def _copy_template(target_dir: Path, project_name: str) -> None:
    """Copy the template directory to target, replacing placeholders."""
    template_ref = resources.files("t0_provider_starter") / "template"

    # Use resources.as_file for proper extraction from packages
    with resources.as_file(template_ref) as template_path:
        _copy_tree(template_path, target_dir, project_name)


def _copy_tree(src: Path, dst: Path, project_name: str) -> None:
    """Recursively copy directory tree, processing template files."""
    dst.mkdir(parents=True, exist_ok=True)

    for item in src.iterdir():
        dest_name = item.name
        if dest_name.endswith(".template"):
            dest_name = dest_name.removesuffix(".template")

        dest_path = dst / dest_name

        if item.is_dir():
            _copy_tree(item, dest_path, project_name)
        else:
            content = item.read_text()
            content = content.replace(TEMPLATE_PLACEHOLDER, project_name)
            dest_path.write_text(content)


def _write_env(target_dir: Path, private_key: str, public_key: str) -> None:
    """Create .env from .env.example with generated keys."""
    env_example = target_dir / ".env.example"
    if not env_example.exists():
        return

    content = env_example.read_text()
    content = content.replace("your_private_key_here", private_key)
    content = content.replace("# your_public_key_here", f"# {public_key}")

    env_path = target_dir / ".env"
    env_path.write_text(content)


if __name__ == "__main__":
    main()
