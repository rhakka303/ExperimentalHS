# Contributing

This is a solo project with one AI collaborator (Claude Code). The workflow below is a lighter version of the one used on [ai-backlog-agent-dashboard](https://github.com/rhakka303/ai-backlog-agent-dashboard) — same shape, far less ceremony. No formal work-item model, no multi-agent handoff protocol, no prioritization framework: those solved problems this project doesn't have.

## Workflow

1. Create an issue before starting a meaningful piece of work.
2. Describe the problem/goal, scope, and acceptance criteria — keep it short.
3. Create a branch, do the work, commit referencing the issue number.
4. Push the branch and open a pull request against `main`.
5. CI must pass on the PR (see below) before it can be merged.
6. Post an **Implementation History** comment on the issue.
7. **The repository owner merges the PR** — not automatic, not done by the AI collaborator. Reviewing the PR before merging *is* the acceptance decision on this project; no separate Acceptance Verification comment is required (this isn't a strict human-in-the-loop showcase project like ai-backlog-agent-dashboard — it's personal, and the owner looks at the issue/PR before merging anyway).
8. The issue closes once its PR is merged (either automatically via a "Closes #N" reference, or manually) — Claude may close it as routine cleanup once that's true, no separate sign-off needed for the close itself.

Issues are created **in segments, tied to the current phase of the project plan** — not all upfront. Each phase (see the repo's phase plan) gets its issues written when work on that phase actually starts, not before.

`main` is branch-protected: force-push and deletion are blocked, and required CI status checks must pass before a PR can be merged.

## Continuous Integration

`.github/workflows/ci.yml` runs automatically on every push and pull request against `main` — no manual trigger needed. Currently checks:

- **No bundled game files** — fails if a ROM, laserdisc video dump, APK, or signing key gets committed (this repo distributes none of those; see README).
- **Markdown lint** — doc quality on `.md` files.

A Gradle/NDK build-and-test CI job doesn't exist yet, even though the Android project itself now does (Phase A) — tracked as its own follow-up, not faked in as a placeholder here.

## Implementation History comment

What actually happened, separate from whether it worked.

```markdown
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

Claims in the Implementation History should be scoped to what was actually tested. "Not independently tested on a physical Retroid Pocket 5" is honest; "works on all hardware" is not, unless it was actually tried.

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
- No formal review/approval workflow beyond CI passing — the owner merges based on their own read of the PR and the Implementation History comment, not a separate review process.
- No separate Acceptance Verification comment/ceremony — merging the PR is the acceptance decision.
- No source-system read-only boundary section — not applicable.

## Security and privacy

- Never commit credentials, signing keystores/passwords, API keys, or personal data.
- No ROMs, laserdisc video dumps, or artwork belong in this repo (see README) — this also means no personal media accidentally committed via test fixtures.
