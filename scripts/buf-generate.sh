#!/bin/sh
# Generates the Node, Python and C# protobuf code from buf.gen.yaml.
#
# That code is not committed. The Git hooks in .githooks/, which this script
# turns on, run it after every checkout, pull and rebase, and CI runs it before
# building an SDK. It does nothing when no input changed since its last run:
# the protos, the Buf configuration, python/uv.lock (the Python connect
# plugin's version) and this script. Deleting or renaming a proto changes its
# directory's modification time, so that counts as a change. `--force`
# generates anyway.
#
# go/api/ is committed and left alone here: `buf generate` from the repository
# root regenerates it, and generate-clients.yaml does so after a proto sync.
set -eu
cd "$(dirname "$0")/.."

# The first run in a clone turns on the Git hooks in .githooks/. A repository
# cannot do that by itself: Git never runs code from a clone its user did not
# enable. The hooks go on to run the hooks Git ran before, from .git/hooks/ and
# a global core.hooksPath.
if [ -z "${CI:-}" ] && git rev-parse --git-dir > /dev/null 2>&1 \
  && [ "$(git config core.hooksPath)" != .githooks ]; then
  git config core.hooksPath .githooks
  echo "Turned on the repository's Git hooks: git config core.hooksPath .githooks" >&2
fi

stamp=.buf-generate.stamp

if [ "${1:-}" != "--force" ] && [ -f "$stamp" ] \
  && [ -d node/sdk/src/common/gen ] \
  && [ -f python/sdk/src/t0_provider_sdk/api/buf/validate/validate_pb2.py ] \
  && [ -d csharp/sdk/T0.ProviderSdk/Api ] \
  && [ -z "$(find proto buf.yaml buf.lock buf.gen.yaml python/uv.lock scripts/buf-generate.sh -newer "$stamp" | head -n 1)" ]; then
  exit 0
fi

rm -f "$stamp"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
# Taken before generating, so an input changed while it runs counts as newer.
touch "$tmp/stamp"
buf generate -o "$tmp/out"

# Replace the previous output only now, so a failed run (offline, rate limited)
# leaves it as it was. Removing it first means a removed proto leaves no
# generated file behind. Only files Git ignores are removed, so the two
# handwritten Python package markers stay.
if git rev-parse --git-dir > /dev/null 2>&1; then
  git clean -fdXq -- node/sdk/src/common/gen python/sdk/src/t0_provider_sdk/api csharp/sdk/T0.ProviderSdk/Api
fi
rm -rf "$tmp/out/go"
cp -R "$tmp/out"/. .
mv "$tmp/stamp" "$stamp"
