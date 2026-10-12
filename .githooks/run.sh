# shellcheck shell=sh
# Sourced by the hooks in this directory, so $0 and "$@" are the hook's own.
#
# Regenerates the protobuf code when a proto or the Buf configuration changed
# (scripts/buf-generate.sh). A failure only warns: Git would otherwise report
# the checkout, pull or rebase itself as failed. The script keeps the previous
# output and retries next time.
"$(git rev-parse --show-toplevel)/scripts/buf-generate.sh" \
  || echo "warning: protobuf code generation failed; run scripts/buf-generate.sh" >&2

# Then the hooks Git ran before .githooks/ was turned on, which it now skips:
# those in .git/hooks/ and in a global core.hooksPath.
name=$(basename "$0")
for dir in "$(git rev-parse --git-common-dir)/hooks" "$(git config --global --path core.hooksPath)"; do
  if [ -n "$dir" ] && [ -x "$dir/$name" ]; then
    "$dir/$name" "$@" || true
  fi
done
exit 0
