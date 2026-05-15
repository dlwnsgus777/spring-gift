---
name: plan-maker
description: Writes a structured Markdown plan document for any task, feature, or project.
  Use when the user requests a "계획 문서", "구현 계획", "실행 계획", "계획 MD로 정리",
  "계획서 작성", or "plan document".
  Trigger on "계획을 작성해줘", "계획 작성해줘",
  "계획을 md 파일에 작성해줘", "계획을 md에 작성해줘",
  or any Korean sentence containing "계획" combined with a writing intent verb
  ("작성", "써줘", "정리", "만들어줘") — even if no specific file is mentioned.
---

# Plan Creator

## Process

### Step 1: Context Gathering

Scan the relevant module's existing code first — controllers, services, domain models, and similar features. This makes your questions targeted rather than generic.

**Implementation Strategy Principles**

When analyzing the work, classify each task by type and sequence them accordingly. Structural changes (no behavior change) should come before behavioral changes — this lowers risk and makes each step independently reviewable.

Apply these in order:

1. **Style cleanup** — Remove dead code, unused imports, duplicate constants. No behavior change.
2. **Service layer extraction** — Move logic tangled in controllers or domain objects into a Service. Structure change only, no behavior change.
3. **Structural change** — Preparatory refactoring to reduce implementation difficulty (e.g., extract method, split class). Existing behavior must remain intact.
4. **Establish transaction boundaries** — Correct `@Transactional` scope. This is a behavioral concern, so complete all structural changes first.
5. **Implement missing behavior** — Add functionality required by the spec that does not yet exist.
6. **Reclaim domain responsibility** — Move business logic sitting in services back into entities or domain objects.
7. **Behavior change** — Intentionally alter existing behavior. Prove safety with tests before and after the change.

> When writing **Section 6 (Implementation Order)**, use these categories to divide steps.
> Never mix a structural change and a behavior change in the same commit — make the separation explicit.

### Step 2: Clarifying Questions

**Ask questions BEFORE writing the document.** If code analysis reveals any decision points or scope ambiguities, do NOT leave them as notes like "별도 확인 필요" inside the document. Instead, ask the user via `AskUserQuestion` first, then write the document after receiving answers.

Situations that require asking:
- Scope decisions (e.g., "A has the same issue — fix it together or separately?")
- Multiple valid implementation approaches
- Ambiguous requirements or missing information

Use `AskUserQuestion` to ask 3–5 questions **in a single call** with **lettered options (A / B / C / D)**. All questions must be written in Korean. Cover: goal/problem, affected modules, API style, data source, and success definition.

Format:
```
Q1. 구현 방식을 선택해 주세요:
A) 신규 REST API 엔드포인트 추가
B) 기존 API 응답 필드 확장
C) 배치 작업 추가
D) 내부 서비스 로직 변경만
```

Wait for answers before writing the plan.

### Step 3: Write the Plan Document

**File Naming**: Determine the filename as follows:
- If the user explicitly specified a filename (e.g., "ffs-contract-cancel.md에 작성해줘"), use that exact name.
- Otherwise, default to `task-{feature}.md` where `{feature}` is a short kebab-case summary of the feature (e.g., `task-consultant-change-cancel.md`, `task-payment-refund.md`).

**File Location**: Always save the plan document to the **project root** (the current working directory). Do NOT create subdirectories (e.g., `.omc/plans/`, `docs/`, etc.) unless the user explicitly specifies a different path.

Read and use the template from `assets/plan-template.md` — fill every section, omit only if truly not applicable.

The template is structured for Spring Boot API feature planning:
- **1. Feature Overview**: Include a screen/function composition table — one row per UI section or feature unit
- **2. API Design**: One subsection per endpoint with Request/Response JSON examples; explicitly cover edge cases (null, empty, etc.)
- **3. Business Logic**: Numbered subsections for each logic area; use a mapping table when status values or enums need display labels
- **4. Implementation Files**: List target classes per module + a package directory tree. After the table and tree, add a **"코드 스니핏"** subsection with skeleton code for each new or modified class — class/record declaration, field stubs, and key method signatures with brief inline comments. Base the snippets on the actual code patterns you found during Step 1. Snippets are scaffolding, not complete implementations, but they should be concrete enough that a developer can start coding immediately without re-reading the requirements. When writing test code snippets, follow the project's test conventions defined in `CLAUDE.md`: method names use sequential numbering (`test01`, `test02`, ...) and test intent is written in `@DisplayName` — never embed the description in the method name itself.
- **5. Considerations & Questions**: Numbered list of items needing confirmation, each with an alternative option if applicable
- **6. Implementation Order (TDD)**: Checkbox list defining the build sequence
- **7. Acceptance Criteria**: Final verification checklist

For non-API work (batch jobs, refactoring, etc.), omit sections that don't apply (e.g., API Design) and fill in the rest.

### Step 4: Link to README.md

After writing the plan document, check if `README.md` exists in the project root. If it does, add a link to the plan file in the relevant section — look for an existing checklist or plan link table that matches the feature's domain or step, and append a markdown link like `[구현 계획](path/to/plan.md)` next to the relevant item.

If no obvious section exists, add the link at the top of the document or under a "## 구현 계획" heading. Do not restructure README.md — minimal, targeted edit only.

### Step 5: Request Feedback (Mandatory)

After writing the document, use the `AskUserQuestion` tool to ask:

> "계획 문서를 작성했습니다. 수정하거나 보완할 부분이 있으신가요?
> 특히 [단계 구성 / 누락된 항목 / 범위]에 대한 의견을 주시면 반영하겠습니다."

Do NOT proceed to implementation without explicit approval.
