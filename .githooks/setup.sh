#!/bin/bash
# One-time setup: validate format before commits/pushes, and lint staged Python.
# NOTE: Must be run in each worktree separately (git config is per-worktree).
set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Use absolute path so hooks work correctly in worktrees
HOOKS_PATH="$REPO_ROOT/.githooks"
git config core.hooksPath "$HOOKS_PATH"
chmod +x "$HOOKS_PATH/pre-commit" "$HOOKS_PATH/pre-push"

echo "Hooks path set to: $HOOKS_PATH"
echo "Commit and push hooks will check Spotless/Prettier without changing or staging files."

# Check if this is a worktree
if [ -f "$REPO_ROOT/.git" ]; then
    echo ""
    echo "Note: This is a git worktree. Hooks are configured for this worktree only."
fi
