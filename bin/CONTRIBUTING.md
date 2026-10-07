# Contributing Guide

This project follows a strict **issue-to-merge workflow**. Please read this
before opening a branch or a pull request.

## 1. Branching

- Every change starts from an **issue**. No issue, no branch.
- Branch naming convention:

  ```
  issue-<number>-<short-slug>
  ```

  Example: `issue-12-add-product-search`

- Branch off the latest `main`.

## 2. Direct pushes are prohibited

- **Never push directly to `main`.** All changes must go through a pull
  request.
- `main` is protected: direct pushes are blocked at the repository level
  (see branch protection settings configured by the repo admin).

## 3. One issue, one PR

- Open **one primary pull request per issue**.
- The PR description must include `Closes #<issue-number>` so the issue
  auto-closes on merge.
- Keep PRs scoped to the linked issue — unrelated changes belong in their
  own issue/PR.

## 4. Pull request requirements

Every PR must:

- Use the [PR template](.github/PULL_REQUEST_TEMPLATE.md) — do not delete
  sections, fill them in or mark N/A.
- Link the issue it closes.
- Include or update automated tests for the change.
- Note any documentation impact (README, API docs, etc.) and update it in
  the same PR when applicable.
- Attach screenshots (mobile **and** desktop) for any UI-facing change.
- Pass CI (build + tests) before it can be merged.
- Be reviewed and approved by someone other than the author.

## 5. Review

- The **author and the approving reviewer must be different people.**
- Address review comments with follow-up commits on the same branch —
  don't force-push over history reviewers have already seen unless asked.
- Once approved and CI is green, the PR is merged by **squash merge**.

## 6. Commit messages

- Write clear, present-tense commit messages (e.g. `Add product search
  endpoint`, not `added stuff`).
- Reference the issue number where useful.

## 7. Out of scope reminders

- Do not implement optional SELLER functionality unless a specific issue
  asks for it.
- Do not add payment, tax, shipping, upload, or cloud integrations unless
  explicitly requested by an issue.

## 8. Getting help

If a requirement in this guide is unclear, ask in the issue or PR thread
before writing code — it's cheaper than redoing a review.
