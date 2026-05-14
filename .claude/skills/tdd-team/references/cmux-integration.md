# cmux Integration for TDD Team

Set up a 3-pane split layout before starting the TDD cycle so each agent phase has its own visible area.

## Target Layout

```
┌─────────────────┬─────────────────┐
│                 │ 🔴 RED          │
│  Claude         ├─────────────────┤
│  (Orchestrator) │ 🟢 GREEN        │
│                 ├─────────────────┤
│                 │ 🔵 REFACTOR     │
└─────────────────┴─────────────────┘
```

## Setup

```bash
# 1. Detect cmux
[ -S "${CMUX_SOCKET_PATH:-$HOME/Library/Application Support/cmux/cmux.sock}" ] || exit 0

# 2. Save caller's pane ref for focus restoration (from cmux tree --json)
CALLER_PANE=$(cmux tree --json | python3 -c "
import json,sys,os
tree=json.load(sys.stdin)
sid=os.environ.get('CMUX_SURFACE_ID','')
for ws in tree.get('workspaces',[]):
  for pane in ws.get('panes',[]):
    for s in pane.get('surfaces',[]):
      if s.get('id','')==sid: print(pane['id'])
" 2>/dev/null)

# 3. Create RED pane (right split — new pane auto-focused)
RED_OUT=$(cmux new-split right)
RED_PANE=$(echo "$RED_OUT" | grep -o 'surface:[0-9]*')
cmux rename-tab --surface "$RED_PANE" "🔴 RED"
cmux send --surface "$RED_PANE" "echo '🔴 RED Agent — waiting for task...'\n"

# 4. Create GREEN pane (split down from RED — new pane auto-focused)
GREEN_OUT=$(cmux new-split down)
GREEN_PANE=$(echo "$GREEN_OUT" | grep -o 'surface:[0-9]*')
cmux rename-tab --surface "$GREEN_PANE" "🟢 GREEN"
cmux send --surface "$GREEN_PANE" "echo '🟢 GREEN Agent — waiting for task...'\n"

# 5. Create REFACTOR pane (split down from GREEN — new pane auto-focused)
REFACTOR_OUT=$(cmux new-split down)
REFACTOR_PANE=$(echo "$REFACTOR_OUT" | grep -o 'surface:[0-9]*')
cmux rename-tab --surface "$REFACTOR_PANE" "🔵 REFACTOR"
cmux send --surface "$REFACTOR_PANE" "echo '🔵 REFACTOR Agent — waiting for task...'\n"

# 6. Return focus to Claude pane
[ -n "$CALLER_PANE" ] && cmux focus-pane --pane "$CALLER_PANE"

# 7. Signal session start
cmux set-status tdd "⏳ Setup" --color "#8e8e93"
cmux log -- "TDD session started — 3 agent panes ready"
```

Store `RED_PANE`, `GREEN_PANE`, `REFACTOR_PANE` as environment context for the session.

## Per-Phase Status Updates

### RED Phase

```bash
cmux set-status tdd "🔴 RED" --color "#ff3b30"
cmux send --surface "$RED_PANE" "echo ''\necho '═══ Cycle {N}: {task description} ═══'\necho '🔴 Writing failing test...'\n"

# After RED agent completes — send key results
cmux send --surface "$RED_PANE" "echo '  Test:    {method name}'\necho '  Failure: {failure message}'\n"
```

### GREEN Phase

```bash
cmux set-status tdd "🟢 GREEN" --color "#34c759"
cmux send --surface "$GREEN_PANE" "echo ''\necho '═══ Cycle {N}: {task description} ═══'\necho '🟢 Implementing...'\n"

# After GREEN agent completes
cmux send --surface "$GREEN_PANE" "echo '  ✅ All tests passing: {N} passed'\n"
```

### REFACTOR Phase

```bash
cmux set-status tdd "🔵 REFACTOR" --color "#007aff"
cmux send --surface "$REFACTOR_PANE" "echo ''\necho '═══ Cycle {N}: {task description} ═══'\necho '🔵 Reviewing code quality...'\n"

# After REFACTOR agent completes
cmux send --surface "$REFACTOR_PANE" "echo '  {refactoring summary or no refactoring needed}'\n"
```

## Cycle Complete

```bash
cmux notify --title "TDD Cycle {N} Complete" --body "Task: {task description} — all tests passing"
cmux log --level success -- "Cycle {N} done: {task description}"

# Add separator to all panes
for PANE in "$RED_PANE" "$GREEN_PANE" "$REFACTOR_PANE"; do
  cmux send --surface "$PANE" "echo '── Cycle {N} Complete ──────────────'\n"
done
```

## Session End Cleanup

```bash
cmux clear-status tdd
cmux clear-progress
cmux notify --title "TDD Session Complete" --body "{N} cycles done — {passed} tests passing"
cmux log --level success -- "TDD session ended: {N} cycles completed"

# Reset pane tab names
cmux rename-tab --surface "$RED_PANE" "terminal"
cmux rename-tab --surface "$GREEN_PANE" "terminal"
cmux rename-tab --surface "$REFACTOR_PANE" "terminal"
```
