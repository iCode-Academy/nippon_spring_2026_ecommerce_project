# Branch Protection Setup (main)

GitHub branch protection is a repo **setting**, not a file, so a repo admin
needs to apply this manually once. Steps below (as of 2026):

1. Go to the repository → **Settings** → **Branches**.
2. Under "Branch protection rules", click **Add rule** (or **Add branch
   ruleset** on newer GitHub UIs).
3. Branch name pattern: `main`
4. Enable:
   - [x] **Require a pull request before merging**
     - [x] Require approvals — set to **1** minimum
     - [x] Dismiss stale pull request approvals when new commits are pushed
     - [x] Require review from Code Owners
   - [x] **Require status checks to pass before merging**
     - Search for and select the CI job name (e.g. `build-and-test`)
     - [x] Require branches to be up to date before merging
   - [x] **Require conversation resolution before merging**
   - [x] **Do not allow bypassing the above settings** (applies rules to
     admins too — recommended so no one can force-push around review/CI)
   - [x] **Restrict who can push to matching branches** → leave empty /
     nobody, so all changes must come through a PR
5. Disable "Allow force pushes" and "Allow deletions" for `main`.
6. Click **Create** / **Save changes**.

## Verifying it worked

- Try pushing a commit directly to `main` locally — it should be rejected.
- Open a PR without any tests changed — CI should still run and the merge
  button should stay disabled until it's green and approved.
- Have the PR author try to approve their own PR — GitHub should not allow
  it to count as the required approval.

This satisfies the CAP-01 acceptance criterion "direct-push prohibition is
documented" (this file) and enforced (the settings above).
