#!/usr/bin/env bash
# Worktree 快捷工具
# 用法：
#   ./scripts/worktree.sh new <branch>   — 创建新 worktree
#   ./scripts/worktree.sh list           — 列出所有 worktree
#   ./scripts/worktree.sh rm <branch>    — 删除 worktree

set -e

ROOT=$(git rev-parse --show-toplevel)
WORKTREES_DIR="$(dirname "$ROOT")/worktrees"

case "$1" in
  new)
    BRANCH="${2:?请提供分支名，例如: ./scripts/worktree.sh new feat/xxx}"
    TARGET="$WORKTREES_DIR/$BRANCH"
    mkdir -p "$(dirname "$TARGET")"
    git worktree add -b "$BRANCH" "$TARGET" main
    echo ""
    echo "✅ Worktree 已创建: $TARGET"
    echo "   cd $TARGET"
    ;;
  list)
    git worktree list
    ;;
  rm)
    BRANCH="${2:?请提供分支名}"
    TARGET="$WORKTREES_DIR/$BRANCH"
    git worktree remove "$TARGET" --force 2>/dev/null || true
    git branch -d "$BRANCH" 2>/dev/null || true
    echo "✅ Worktree 已删除: $BRANCH"
    ;;
  *)
    echo "用法:"
    echo "  ./scripts/worktree.sh new <branch>   创建新 worktree（基于 main）"
    echo "  ./scripts/worktree.sh list           列出所有 worktree"
    echo "  ./scripts/worktree.sh rm <branch>    删除 worktree 和分支"
    ;;
esac
