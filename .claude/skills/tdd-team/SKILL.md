---
name: TDD Team
version: 0.4.0
description: >
  Use this skill when the user wants to develop features using Test-Driven Development
  with an agentic Red-Green-Refactor cycle. Trigger on "start TDD", "do TDD",
  "TDD로 개발해줘", "TDD 시작", "TDD 팀 만들어", "테스트 주도 개발",
  "red green refactor", "test-driven development", "테스트 먼저 작성하고 싶어",
  "write tests first then implement", "테스트부터 짜줘", "TDD 방식으로 구현해줘".
  Also trigger when a user describes a feature and says they want it built incrementally
  with tests, e.g. "이 기능 테스트 먼저 만들고 하나씩 구현하자", "build this with
  failing tests first", or "한 단계씩 테스트 작성하면서 개발하고 싶어".
  Do NOT trigger for simply writing unit tests after implementation, running existing
  tests, or debugging test failures — those are not TDD workflows.
---

# TDD Team

Orchestrate a 3-phase Red-Green-Refactor TDD cycle using sequential Agent calls. Each cycle implements one small behavior increment: write a failing test, make it pass with minimal code, then clean up.

## Agent Roles

| Agent | Phase | Responsibility |
|-------|-------|----------------|
| **red** | RED — Write failing test | Create a test that compiles but fails, then verify the failure |
| **green** | GREEN — Make it pass | Implement the simplest code to make the test pass |
| **refactor** | REFACTOR — Clean up | Improve code quality while keeping all tests passing |

## Setup

### 0. cmux (Optional)

If cmux is available, read `references/cmux-integration.md` to set up a 3-pane split layout (RED / GREEN / REFACTOR) on the right side and sidebar status indicators. Capture `RED_PANE`, `GREEN_PANE`, `REFACTOR_PANE` surface IDs for use throughout the session. Skip if cmux is not detected.

### 1. Detect Environment

Check for build files (`build.gradle.kts`, `pom.xml`, `package.json`, etc.) and determine the test command. Capture as environment context:

```
PROJECT_ROOT / SOURCE_DIR / TEST_DIR / TEST_CMD / TEST_FRAMEWORK
CMUX_ENABLED (true/false) / RED_PANE / GREEN_PANE / REFACTOR_PANE
```

### 2. Decompose into TDD Tasks

Break the feature into small, incremental behaviors — each becomes one TDD cycle. Name each as a behavior statement: "returns X when Y". Example for "Calculator 클래스":

```
1. add(1, 2) returns 3
2. subtract(5, 3) returns 2
3. divide(10, 0) throws ArithmeticException
```

Present the task list and get user confirmation before starting.

## TDD Cycle Execution

For each task, run three sequential Agent calls. Read `references/agent-prompts.md` for the full system prompts.

### RED Phase

Spawn agent with Red prompt + environment context + task description. Key inputs/outputs:
- **Input**: task description, existing file paths
- **Output**: test file path, method name, failure message
- If test already passes → skip GREEN. If build fails → fix stubs, re-verify.

> ⚠️ **A compilation error is NOT Red.**
> Red requires the test to actually run and fail. Two valid Red states:
> - **New class/method**: stub exists with `throw new UnsupportedOperationException("Not implemented yet")` — test runs and throws this exception → Red confirmed.
> - **Existing test modified/added**: test runs and the assertion fails → Red confirmed.
>
> If the build fails, fix stubs until compilation passes, then verify the test failure.

### GREEN Phase

Spawn agent with Green prompt + RED's failure output. Key inputs/outputs:
- **Input**: failing test path, failure message
- **Output**: files modified, all test results
- Write the minimum code to pass. If still failing, retry with different approach.

### REFACTOR Phase

**Conditional execution**: Skip this phase if the GREEN implementation is already clean. Simple behaviors (basic arithmetic, simple returns, etc.) rarely need refactoring.

Refactoring techniques must follow Martin Fowler's catalog (*Refactoring: Improving the Design of Existing Code*). Apply named techniques (e.g., Extract Method, Rename Variable, Replace Conditional with Polymorphism) rather than ad-hoc cleanup.

Refactoring scope includes **both production code and test code**. Test code is not exempt — improve test readability, extract helper methods, and clean up assertion style as needed.

Spawn agent with Refactor prompt + current source/test files. Key inputs/outputs:
- **Input**: summary of RED+GREEN changes
- **Output**: what changed and why (or "no refactoring needed"), final test results
- Agent identifies ALL opportunities first, applies them in one batch, then runs tests once. Only falls back to incremental if the batch fails.

### Cycle Summary and User Checkpoint

⛔ **MANDATORY STOP after every task — including task 1.**

After each task (RED→GREEN→REFACTOR cycle), you MUST:

1. Present the cycle summary in this format:

```
── TDD Cycle {N} Complete ──
RED:      ✅ Test written: {method name}
GREEN:    ✅ Implementation: {summary}
REFACTOR: ✅ {what changed / "no refactoring needed"}
Tests: {N} passed, 0 failed

[x] 1. {done}   [>] 2. {current}   [ ] 3. {next}
```

2. Request feedback using this exact format:

> "이 구현에 대한 피드백을 주실 수 있으신가요?
> 특히 [테스트 커버리지 / 구현 방식 / 설계 결정] 부분에 대한 의견을 주시면 반영하겠습니다."

3. **Wait silently.** Do NOT proceed to the next task under any circumstance until the user sends an explicit approval message (e.g., "진행해", "다음", "계속", "LGTM", "ok", "좋아").

4. If the user gives feedback without approving, incorporate the feedback and repeat this checkpoint.

**There are NO exceptions to this rule.** Even if the next task is trivial, even if no issues were found — always stop and wait for explicit user approval before moving to the next task.

If cmux is available: `cmux notify` on cycle complete (see `references/cmux-integration.md`).

## Key Principles

**Definition of Red** — Red means the build passes, the test runs, and the assertion fails. A compilation error is NOT Red. If the build fails, fix stubs until compilation passes, then verify the test failure.

**Strict phase separation** — RED writes tests only, GREEN writes production code only, REFACTOR changes no behavior. This ensures tests genuinely validate behavior rather than passing alongside code written at the same time.

**Simplest implementation first** — GREEN can hardcode values. Elegance comes from REFACTOR. If hardcoding passes the test, the test probably needs a stricter assertion.

**One behavior per cycle** — Small steps build confidence and keep the feedback loop tight.

## Error Handling

| Situation | Action |
|-----------|--------|
| Build fails in RED | Fix stubs, re-verify failure |
| GREEN can't pass test | Retry with different approach |
| REFACTOR breaks tests | Revert and try smaller changes |

## Session End

Provide a final summary: cycles completed, files changed, final test count, next steps.
If cmux was used, run cleanup from `references/cmux-integration.md`.

## Reference Files

- **`references/agent-prompts.md`** — Full prompts for Red, Green, and Refactor agents
- **`references/cmux-integration.md`** — cmux pane layout and status commands (read only if cmux available)
