# Developer Guide: Maintaining This Fork

This document explains how to maintain this fork of Cryptomator and keep it synchronized with the official upstream repository.

## Overview

This is a fork of the official [Cryptomator](https://github.com/cryptomator/cryptomator) repository with custom features. We maintain our changes separately while regularly pulling in bug fixes and new features from the upstream project.

## Initial Setup

### 1. Clone Your Fork

```bash
git clone <your-fork-url>
cd cryptomator
```

### 2. Add Upstream Remote

Add the official Cryptomator repository as an upstream remote (if not already done):

```bash
git remote add upstream https://github.com/cryptomator/cryptomator.git
git fetch upstream
```

Verify your remotes:

```bash
git remote -v
# Should show:
# origin    <your-fork> (fetch/push)
# upstream  https://github.com/cryptomator/cryptomator.git (fetch/push)
```

## Branch Structure

Our fork uses the following branch strategy:

- **`main`** - Clean mirror of upstream, no custom changes
  - Tracks `upstream/main`
  - Always kept in sync with official Cryptomator
  - Never commit custom changes here

- **`feature/plausible-deniability`** - Our custom plausible deniability feature
  - Based on `main` branch
  - Contains our specific modifications
  - Regularly rebased on top of `main`

- **Additional feature branches** - Add more as needed
  - Keep each feature isolated
  - Makes it easier to manage conflicts
  - Allows selective deployment of features

## Syncing with Upstream

### Using the Sync Script (Recommended)

We provide a convenient script to automate the sync process:

```bash
./sync-fork.sh
```

This script will:
1. Fetch latest changes from upstream
2. Update your local `main` branch
3. Push updated `main` to your fork
4. Rebase all feature branches on top of `main`
5. Push updated feature branches (with --force-with-lease)

### Manual Sync Process

If you prefer to sync manually or need more control:

#### Step 1: Update Main Branch

```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
```

#### Step 2: Update Feature Branches

For each feature branch:

```bash
git checkout feature/plausible-deniability
git rebase main
git push --force-with-lease origin feature/plausible-deniability
```

**Important**: Use `--force-with-lease` instead of `--force`. It's safer because it won't overwrite changes if someone else pushed to your branch.

## Handling Merge Conflicts

Conflicts are normal when rebasing your changes on top of upstream updates.

### During Rebase

If you encounter conflicts during `git rebase main`:

1. **View conflicted files**:
   ```bash
   git status
   ```

2. **Edit conflicted files** to resolve conflicts:
   - Look for `<<<<<<<`, `=======`, and `>>>>>>>` markers
   - Keep the code that makes sense (yours, theirs, or a combination)
   - Remove the conflict markers

3. **Stage resolved files**:
   ```bash
   git add <resolved-file>
   ```

4. **Continue the rebase**:
   ```bash
   git rebase --continue
   ```

5. **Or abort if needed**:
   ```bash
   git rebase --abort
   ```

### Conflict Resolution Tips

- **Test after resolving**: Always build and test after resolving conflicts
- **Understand both changes**: Read both the upstream change and your change
- **Ask for help**: If unsure, consult the upstream commit history
- **Document tricky conflicts**: Add comments explaining why you resolved conflicts in a particular way

## Sync Frequency

**Recommended**: Sync every 1-4 weeks

- **More frequent** = Smaller conflicts, easier to resolve
- **Less frequent** = Larger conflicts, more work to resolve

Monitor the [official Cryptomator releases](https://github.com/cryptomator/cryptomator/releases) and sync when:
- Major security fixes are released
- New features you want are added
- Before starting new development work

## Adding New Feature Branches

To add a new feature branch to the sync process:

1. **Create the branch** from `main`:
   ```bash
   git checkout main
   git checkout -b feature/my-new-feature
   ```

2. **Update sync-fork.sh** to include the new branch:
   ```bash
   for branch in feature/plausible-deniability feature/my-new-feature; do
   ```

3. **Develop your feature** and push:
   ```bash
   git push origin feature/my-new-feature
   ```

## Best Practices

### 1. Keep Main Clean
- Never commit directly to `main`
- Only merge/pull from upstream
- Use `main` as the base for all feature branches

### 2. Feature Isolation
- One feature per branch
- Keeps conflicts manageable
- Easier to drop features if needed
- Can share individual features with others

### 3. Commit Messages
- Use clear, descriptive commit messages
- Prefix custom commits with `[FORK]` or similar
- Makes it easy to identify custom vs upstream changes

### 4. Documentation
- Document why you made custom changes
- Add comments explaining deviations from upstream
- Update this file when changing workflows

### 5. Testing
- Always test after syncing
- Run the full test suite: `mvn test`
- Manually test critical features
- Verify your custom features still work

## Building and Running

### Build the Project

```bash
mvn clean install
```

### Run Cryptomator

```bash
./run-cryptomator.sh
```

Or using Maven:

```bash
mvn javafx:run
```

## Troubleshooting

### "Already up to date" but still conflicts
- Your `main` might have diverged from upstream
- Reset main: `git checkout main && git reset --hard upstream/main`
- Then rebase feature branches

### Force push rejected
- Someone else pushed to your branch
- Use `git pull --rebase` first, or
- Use `--force` if you're sure (but be careful!)

### Build fails after sync
- Upstream may have changed dependencies
- Run: `mvn clean install -U` (updates dependencies)
- Check if upstream changed build requirements

### Sync script fails mid-way
- Check git status: `git status`
- Complete or abort current operation
- Fix the issue (often conflicts)
- Rerun the script or continue manually

## Useful Commands

```bash
# View upstream changes before syncing
git fetch upstream
git log main..upstream/main

# See what branches exist
git branch -a

# See which commits are unique to your feature branch
git log main..feature/plausible-deniability

# Create a combined feature branch
git checkout -b feature/combined main
git merge feature/plausible-deniability
git merge feature/another-feature

# Temporarily save uncommitted changes
git stash
git checkout main
# ... do sync ...
git checkout feature/plausible-deniability
git stash pop
```

## Contributing Back to Upstream

If you develop a feature that would benefit the official Cryptomator project:

1. Create a clean branch from upstream/main
2. Cherry-pick only the relevant commits
3. Follow upstream's contribution guidelines
4. Submit a Pull Request to the official repository

Note: Keep your fork's branch separate from the PR branch to avoid complications.

## Resources

- [Official Cryptomator Repository](https://github.com/cryptomator/cryptomator)
- [Cryptomator Documentation](https://docs.cryptomator.org/)
- [Git Rebase Documentation](https://git-scm.com/docs/git-rebase)
- [Syncing a Fork (GitHub Docs)](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/syncing-a-fork)

---

**Last Updated**: November 2025

