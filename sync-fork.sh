#!/bin/bash
set -e

# Parse command line options
SKIP_PUSH=false
if [[ "$1" == "--no-push" ]]; then
    SKIP_PUSH=true
    echo "Running in local-only mode (no push to origin)"
fi

echo "Fetching upstream..."
git fetch upstream

echo "Updating main..."
git checkout main
git merge upstream/main

if [ "$SKIP_PUSH" = false ]; then
    echo "Pushing main to origin..."
    git push origin main || echo "Warning: Could not push main (continuing anyway)"
else
    echo "Skipping push to origin (use without --no-push to enable)"
fi

echo "Updating feature branches..."
# Add more branches here later if needed
for branch in feature/plausible-deniability; do
    echo "  Rebasing $branch..."
    git checkout $branch
    git rebase main
    
    if [ "$SKIP_PUSH" = false ]; then
        echo "  Pushing $branch to origin..."
        git push --force-with-lease origin $branch || echo "  Warning: Could not push $branch (continuing anyway)"
    fi
done

echo ""
echo "Done! All branches synced locally."
if [ "$SKIP_PUSH" = false ]; then
    echo "Changes have been pushed to your fork on GitHub."
else
    echo "Changes are local only. Push manually with: git push origin <branch>"
fi