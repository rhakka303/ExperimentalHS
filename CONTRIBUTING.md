# Contributing

This is a solo project with one AI collaborator (Claude Code). The workflow below is a lighter version of the one used on [ai-backlog-agent-dashboard](https://github.com/rhakka303/ai-backlog-agent-dashboard) — same shape, far less ceremony. No formal work-item model, no multi-agent handoff protocol, no prioritization framework: those solved problems this project doesn't have.

## Workflow

1. Create an issue before starting a meaningful piece of work.
2. Describe the problem/goal, scope, and acceptance criteria — keep it short.
3. Do the work.
4. Post an **Implementation History** comment.
5. Post a separate **Acceptance Verification** comment.
6. Close the issue only after both comments are posted.

Issues are created **in segments, tied to the current phase of the project plan** — not all upfront. Each phase (see the repo's phase plan) gets its issues written when work on that phase actually starts, not before.

Direct commits to `main` referencing the issue number are fine for this project's pace; a PR is optional, not required, unless a change is large enough that reviewing it as a diff is actually useful.

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
- No mandatory PR-per-change — direct commits are fine for solo pace.
- No source-system read-only boundary section — not applicable.

## Security and privacy

- Never commit credentials, signing keystores/passwords, API keys, or personal data.
- No ROMs, laserdisc video dumps, or artwork belong in this repo (see README) — this also means no personal media accidentally committed via test fixtures.
