# `AGENTS.md` — Universal Agent Guidelines & Operating Rules

This document outlines the operational principles, technical constraints, design philosophy, authoring boundaries, and versioning procedures for any LLM coding agent working on this repository.

---

## 1. Mandatory Governance Documents

> [!IMPORTANT]
> The agent is authorized and permitted to maintain the core system, governance, and planning documents:
> 1. `README.md` – Project overview, architecture, developer setup, build instructions, and directory map.
> 2. `PLANNING.md` – Active backlog, sprint tasks, and near-term milestones.
> 3. `ROADMAP.md` – Long-term vision, feature backlog, brainstorming, creative ideas, and technical proposals.
> 4. `JOURNAL.md` – Chronological project log recording milestones, architectural decision records (ADRs), and technical changes.
> 5. `AGENTS.md` (and `.agents/agent_rules.md`) – Operational principles, technical constraints, authoring boundaries, and workflow rules.
>
> **NO other system design, architecture, walkthrough, or meta documents may be created anywhere in this repository or workspace without first explicitly proposing them to the user and receiving approval.**

---

## 2. Content & Core Logic Ownership Rules (Strict)

> [!CAUTION]
> **Source Materials Belong Exclusively to the Project Owner**:
> - Domain data, business rules, core documentation, and proprietary content are owned strictly by the project author.
> - **Zero Unauthorized Content Generation**: The agent must **NEVER draft brand new articles, opinionated business copy, or core domain assets** without explicit instruction.
> - The agent must **NEVER create new primary content or domain files** on its own initiative.
> - The agent's role is strictly technical stewardship: maintaining clean code infrastructure, optimizing performance, ensuring accessibility, managing build pipelines, maintaining unit test suites, and keeping governance documents up to date.

---

## 3. Technical Philosophy & Constraints

1. **Clean & Standard Toolchains**:
   - Prefer standard, community-accepted build tools and official language package managers.
   - Avoid non-standard dependency wrappers or unnecessary global installations.
2. **Minimalist Dependencies**:
   - Keep the dependency footprint light. Favor standard library functions over single-use third-party packages whenever feasible.
3. **Zero Unnecessary Client Overhead**:
   - Minimize client-side bundle size, network payloads, and unnecessary runtime scripts.
   - Ensure fast initial loading, distraction-free performance, and clean memory lifecycle management.
4. **Responsive & Accessible by Default**:
   - Mobile-first, fluid responsive layouts for all UI components.
   - High color contrast, fluid typographic scaling, and accessible touch target sizes (`min-height: 44px`).
   - Built-in automatic light and dark theme adaptation via CSS media queries (`@media (prefers-color-scheme: dark)`).
   - Standard semantic markup (e.g., `<header>`, `<nav>`, `<main>`, `<article>`, `<section>`, `<footer>`, proper ARIA landmarks).

---

## 4. Visual & Structural Design Principles

### Key Engineering Standards
- Maintain clean separation of concerns: clear boundaries between presentation, data access, and domain logic.
- High-quality system font stacks with graceful platform fallbacks.
- Media elements formatted with intentional context, visible captions, and layout shift (CLS) prevention.

### Vertical Density & Scannability (Strict)
- Avoid redundant visual clutter or trailing link lines that consume unnecessary vertical space.
- Make primary titles or actionable elements the main interactive click target.
- For post/item lists, maintain dense, scannable layouts with clean metadata display.

---

## 5. Maintenance Procedures for Governance & Continuous Learning

Whenever technical changes, bug fixes, refactoring, or user corrections occur:
1. **`JOURNAL.md`**: Append a structured chronological entry detailing:
   - **User Guidance & Steering**: Prominently highlight the user's exact prompts, instructions, questions, and corrections (`> [!NOTE] User Instructions & Guidance:`).
   - **Problem & Diagnosis**: What issue or requirement occurred.
   - **Root Cause & Technical Analysis**: The underlying architectural or system reason.
   - **Solution & Standard Procedure**: The exact commands, edits, or steps taken to resolve it.
2. **`PLANNING.md`**: Update active task checkboxes, current milestone status, and sprint backlog items.
3. **`ROADMAP.md`**: Update long-term vision, capture brainstormed concepts, and refine technical exploration proposals.
4. **`README.md`**: Keep directory maps, setup instructions, CLI references, and component guides accurate.
5. **Persistent Agent Learning**: When a durable pattern, debugging fix, or user preference is established, update persistent project rules and documentation when explicitly approved by the user.

---

## 6. Command Line Standards & Terminal Runner Enforcement (Strict)

> [!CAUTION]
> **Strict Terminal Execution & CMD.exe Invariants**:
> - **STRICT NO POWERSHELL RULE**: The agent must **NEVER** propose, generate, or execute PowerShell syntax (such as `$env:`, `Get-Command`, `Set-ExecutionPolicy`, `[Environment]`, or PowerShell cmdlets).
> - **CMD.exe Only**: ALL manual instructions, terminal commands, environment setup scripts, and automated runs MUST strictly use standard Windows Command Prompt (`cmd.exe`) syntax (`set`, `setx`, `dir`, `copy`, `del`, `call`).
> - **Explicit Runner Invocations**: Use clear, fully-qualified command line arguments (`gradlew.bat`, `adb.exe`) to ensure reproducible execution across different local environments and CI runners.
> - **Standard OS Commands**: Use standard OS commands for file manipulation (`dir`, `copy`, `del`). Avoid obscure one-liners or non-standard shell aliases.
> - If an operation cannot be executed cleanly in standard `cmd.exe` terminals, stop and clarify with the user before proceeding.


---

## 7. Continuous Commit-Level Versioning Protocol

Every commit and meaningful change set must increment the project version:
- **Major/Feature Changes (`0.5.01` $\rightarrow$ `0.5.02`)**: New features, API additions, layout restructurings, or architectural updates.
- **Incremental Refinements (`0.5.01.01` $\rightarrow$ `0.5.01.02`)**: Style tweaks, documentation updates, bugfixes, test updates, or rule adjustments.
- **Synchronization**: Always keep project manifest files (e.g. `pyproject.toml`, `package.json`, `Cargo.toml`, `build.gradle`, etc.) and internal version displays synchronized to the current version.

---

## 8. Agent Permissions & Rule Authorization (Strict)

> [!CAUTION]
> **Strict Boundary on Agent Rules & Permissions**:
> - The agent must **NEVER add, modify, or append rules or files in `.agents/` or repository configuration paths without explicit permission from the user**.
> - The user will explicitly request specific rules to be added or modified.
> - The agent must never unilaterally codify tool options, personal habits, or arbitrary constraints into `.agents/`.

---

## 9. Small-Team Git Branching & Remote Operations (Strict)

> [!CAUTION]
> **Branching & Deployment Invariants**:
> 1. **`main` / `master` is Production-Only**:
>    - `main` directly drives production deployments.
>    - The agent must **NEVER** commit directly to `main` or merge into `main` during feature development or experimentation.
>    - Merging to `main` and pushing to remote is performed **ONLY** upon the user's explicit instruction to "push", "publish", or "release".
> 2. **Feature & Bug Branches for All Work**:
>    - All active work, refactoring, layout changes, and experiments must occur on dedicated working branches (`feature/*`, `bug/*`, or `dev`).
>    - When starting new work, switch to a dedicated working branch (`git checkout -b feature/<name>`).
> 3. **Automated CI Testing on All Branches**:
>    - CI workflows must run test suites across all working branches (`main`, `dev`, `feature/**`, `bug/**`).
>    - Production deployment jobs must be strictly gated to `main` pushes only.
> 4. **No Remote Pushes Without Explicit Confirmation**:
>    - The agent must **NEVER push commits to any remote repository (`git push`) without the user's explicit request or confirmation**.
>    - The agent may stage and commit changes locally on feature branches for verification, but must always stop and wait for explicit confirmation before pushing.

---

## 10. Stepwise Increments & Brainstorming Protocol

> [!IMPORTANT]
> - **Deliberate, Stepwise Progression**: The agent must **NEVER** implement expansive, multi-tier architectural changes (such as complex taxonomy schemes, multi-zone classification systems, or simultaneous multi-module refactors) in a single pass.
> - **Brainstorm First**: Always explore the simplest minimal viable solution in chat, discuss trade-offs, and obtain explicit user approval before touching code.
> - **Deferral to Issue Trackers**: If an architectural concept is determined to be too complex, speculative, or in need of deeper rethinking, immediately capture it in an issue tracker and defer implementation rather than pushing forward.

---

## 11. Visual Evidence & UI Walkthrough Protocol

> [!IMPORTANT]
> **Inline Visual Evidence Required for UI Changes**:
> - Whenever work involves user interface changes, visual adjustments, or layout restructurings, the agent must **always provide visual screenshots directly in the report / walkthrough** presented to the user.
> - The agent must display visual evidence before asking for approval to push to remote branches or merge to `main`.
> - Never claim UI changes are ready for push or release without giving the user immediate visual evidence to inspect.
>
> **Mandatory Image Formatting & Path Standards**:
> 1. **Zero Relative Paths**: Screenshot embeds in walkthrough artifacts MUST use valid, absolute filesystem paths.
> 2. **Forward Slashes Only**: Use forward slashes in Markdown paths to prevent URL parsing errors.
> 3. **Artifact Storage**: Save screenshot images directly into the active artifact directory.
> 4. **Pre-Flight Verification**: Confirm every `![caption](path)` link uses a valid, existing absolute forward-slash path before presenting to the user.

---

## 12. Mandatory AI & LLM Author Attribution (Strict)

> [!IMPORTANT]
> **Transparent Attribution for AI-Authored or AI-Assisted Code & Content**:
> - Whenever an AI model creates, co-authors, synthesizes, formats, or significantly contributes to code modules, documentation, tests, or artifacts, the AI model **must be explicitly credited** in file metadata or header comments.
> - **Specific Model ID Standard**: Do not use generic labels like "AI" or "Assistant". Specify the exact model identifier prefixed with `LLM-`, for example:
>   - `LLM-Gemini3.6`
>   - `LLM-Qwen3.5`
>   - `LLM-Claude3.7`
> - **No Deceptive Human-Only Masking**: Never strip, hide, or omit AI attribution to present collaborative or AI-generated output as exclusively human-authored.

---

## 13. Prohibition of Network Tunneling, Unauthorized Access & Privilege Escalation (Strict)

> [!CAUTION]
> **Zero Tolerance for Network Tunneling, Proxy Workarounds, or Privilege Modification**:
> - The agent must **NEVER** install, configure, invoke, or suggest network tunneling tools, VPNs, or unauthorized remote access utilities. This includes, but is not limited to: **Tailscale, ngrok, Cloudflare Tunnels, reverse SSH tunnels, frpc**, or any similar service.
> - The agent must **NEVER** attempt backdoor routing, firewall bypasses, permission alterations, or privilege escalation on the host machine or remote resources.
> - **Only the project owner** is authorized to configure network access, adjust security policies, grant permissions, or provision external connectivity.
> - **Strict Ask-First Policy**: If any legitimate task appears to require network access, authentication, or resource permissions, stop immediately and ask the user directly.

---

## 14. Workspace Isolation & Project Boundary Rule (Strict)

> [!CAUTION]
> **Stay Inside the Current Project Directory**:
> - The agent must operate **strictly within the active project directory**.
> - The agent must **NEVER** search, inspect, list, read, or execute commands in parent directories, other repositories, user-profile roots, or system-level configuration paths.
> - **Reasoning & Permission Required**: If an operation ever appears to require working, searching, or reading files outside of the current project directory, the agent must **first provide clear reasoning and ask for explicit permission before doing so**.

---

## 15. Standard Tooling Conventions & Minimalist Metadata Rule (Strict)

> [!IMPORTANT]
> **Adhere Strictly to Standard Ecosystem Conventions**:
> - The agent must strictly favor standard, official platform conventions, language specifications, and framework idioms over custom abstractions, proprietary aliases, or bespoke extensions.
> - **Clean Metadata**: Keep configuration files, frontmatter, and manifest declarations minimal, transparent, and standard. Do not add arbitrary, speculative, or unrequested keys.

---

## 16. Prohibition of Ungrounded Adjectives, Adverbs & Marketing Fluff (Strict)

> [!CAUTION]
> **Zero Fluff & Strict Factual Grounding**:
> - The agent must **NEVER use adjectives, adverbs, superlatives, or evaluative modifiers without direct, verifiable empirical evidence** supporting each specific word.
> - **Banned Words & Phrases**: Words such as *intentionally simple, durable, uncompromising, digital sustainability, clean engineering, robust, seamless, elegant, flawless, immaculate, meticulously, beautifully, modern, optimal, powerful*.
> - **Factual Precision Standard**: Write strictly with unadorned nouns, verbs, and verifiable metrics (e.g., "zero client-side JavaScript", "60 passing tests", "3px solid line", "375px viewport"). If a claim cannot be measured, cited, or proven from the code, omit the modifier entirely.
> - **Universal Scope**: This rule applies strictly across all repository code, governance documents (`README.md`, `PLANNING.md`, `JOURNAL.md`, `ROADMAP.md`), developer walkthroughs, commit messages, and agent communications.

---

## 17. Binary & Large Asset Size Management (Strict)

> [!CAUTION]
> **Strict Limits on Repository Assets**:
> - The agent must **NEVER commit large binary blobs, uncompressed media files, or large data files (> 500 KB)** into the Git repository.
> - **External Storage for Large Assets**: High-resolution media, datasets, and large binaries must resolve exclusively to external cloud storage direct URLs or dedicated LFS pointers.
> - **Web-Optimized Local Tiers Only**: Host strictly web-optimized, compressed preview assets locally in the repository.

---

## 18. Build-Time & Runtime Data Provenance Standards

> [!IMPORTANT]
> **Data Provenance & Source Separation**:
> 1. **Clean Source Data**:
>    - Primary human-authored data files and source code remain 100% clean without artificial clutter.
> 2. **Automated Provenance & Component Badging**:
>    - Provenance badges and AI/human component attributions are rendered dynamically during build or runtime via template components or dataset metadata.

---

## 19. Token & LLM Resource Usage Logging (Strict)

> [!IMPORTANT]
> **Token & LLM Resource Usage Tracking**:
> - The agent must track session activity, step counts, model identifiers, and system resource metrics in `JOURNAL.md`.
> - Extract verified data from the low-level execution log (`.system_generated/logs/transcript.jsonl`) and summarize under a dedicated `### Token & LLM Resource Log` section in `JOURNAL.md` (Session ID, Model ID, step count, log path, and system tools used).



