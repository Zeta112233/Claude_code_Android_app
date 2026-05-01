#!/bin/bash
# 将仓库中的 Claude Code memory 文件同步到本机正确路径
# 使用方法：clone 仓库后执行 bash .claude-memory/sync_memory.sh

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
MEMORY_DIR="$HOME/.claude/projects/C--ZRS-Works/memory"

mkdir -p "$MEMORY_DIR"

cp -v "$REPO_DIR/MEMORY.md" "$MEMORY_DIR/MEMORY.md"
cp -v "$REPO_DIR/project_claude_test_app.md" "$MEMORY_DIR/project_claude_test_app.md"

echo ""
echo "Claude Code memory 已同步到：$MEMORY_DIR"
echo "重新打开 Claude Code 即可继续上下文。"
