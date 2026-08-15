# Contributing

This is a solo project with one AI collaborator (Claude Code). The workflow below is a lighter version of the one used on [ai-backlog-agent-dashboard](https://github.com/rhakka303/ai-backlog-agent-dashboard) — same shape, far less ceremony. No formal work-item model, no multi-agent handoff protocol, no prioritization framework: those solved problems this project doesn't have.

## Workflow

1. Create an issue before starting a meaningful piece of work.
2. Describe the problem/goal, scope, and acceptance criteria — keep it short.
3. Create a branch, do the work, commit referencing the issue number.
4. Push the branch and open a pull request against `main`.
5. CI must pass on the PR (see below) before it can be merged.
6. Post an **Implementation History** comment on the issue.
7. Post a separate **Acceptance Verification** comment on the issue.
8. **The repository owner merges the PR** — not automatic, not done by the AI collaborator. Close the issue only after both comments are posted and the PR is merged.

Issues are created **in segments, tied to the current phase of the project plan** — not all upfront. Each phase (see the repo's phase plan) gets its issues written when work on that phase actually starts, not before.

`main` is branch-protected: force-push and deletion are blocked, and required CI status checks must pass before a PR can be merged.

## Continuous Integration

`.github/workflows/ci.yml` runs automatically on every push and pull request against `main` — no manual trigger needed. Currently checks:

- **No bundled game files** — fails if a ROM, laserdisc video dump, APK, or signing key gets committed (this repo distributes none of those; see README).
- **Markdown lint** — doc quality on `.md` files.

A Gradle/NDK build-and-test job will be added once the Android project exists (Phase A/C) — tracked as its own follow-up, not faked in as a placeholder now.

## Implementation History comment

What actually happened, separate from whether it worked.

```
## Implementation History

### Changes made
1. ...

### Files changed
- `path/file`: description

### Validation performed
- Build:
- On-device test:

### Known limitations
- ...
```

## Acceptance Verification comment

Whether the work satisfies the issue's stated acceptance criteria — separate from Implementation History, not a substitute for it.

```
## Acceptance Verification

- [ ] Criterion 1 — Pass/Fail — evidence
- [ ] Criterion 2 — Pass/Fail — evidence

### Closure decision
Close / Do not close — reason
```

Claims should be scoped to what was actually tested. "Not independently tested on a physical Retroid Pocket 5" is honest; "works on all hardware" is not, unless it was actually tried.

## Issue content

Keep it to:

- Problem or goal
- Scope (in / out)
- Acceptance criteria

Skip the rest of the canonical fields from the ai-backlog model (source-system identity, estimates, sprint assignment, etc.) — this project has no source system and no sprints.

## What's dropped from the ai-backlog rules

- No dual-AI handoff protocol (single AI collaborator here).
- No WSJF/prioritization scoring — phase order is already set by the project plan.
- No formal Story/Bug/Enabler work-item model — plain issues, optionally labeled.
- No formal review/approval workflow beyond CI passing — the owner merges based on their own read of the PR and the Implementation History/Acceptance Verification comments, not a separate review process.
- No source-system read-only boundary section — not applicable.

## Security and privacy

- Never commit credentials, signing keystores/passwords, API keys, or personal data.
- No ROMs, laserdisc video dumps, or artwork belong in this repo (see README) — this also means no personal media accidentally committed via test fixtures.
