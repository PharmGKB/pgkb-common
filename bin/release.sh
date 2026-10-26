#!/bin/bash
#
# Performs a release on PharmGKB/pgkb-common.
#
# semantic-release tags development and creates the GitHub release, which triggers
# .github/workflows/release.yml to publish to Maven Central. main is then fast-forwarded to development.
#
set -e
set -u
set -o pipefail


branch=$(git rev-parse --abbrev-ref HEAD)
if [[ $branch != 'development' ]]
then
  echo "Must run on development branch."
  exit 1
fi

# HEAD, so staged changes count too
diffs=$(git diff HEAD | wc -l)
if [[ $diffs -gt 0 ]]
then
  echo "There are uncommitted changes."
  exit 1
fi

cd "$(git rev-parse --show-toplevel)"


# do release
echo "Installing dependencies..."
pnpm install --frozen-lockfile

echo ""
echo "Creating release..."
pnpm release


# update repo
echo ""
echo "Updating repo..."
git fetch


# update main branch
echo ""
echo "Updating main branch..."
git checkout main
git pull
# --ff-only: fail rather than rewrite main if it has commits development lacks
git merge --ff-only development
git push

# switching back to development
git checkout development


echo ""
echo "Done."
