#!/usr/bin/env sh
set -eu

git config core.hooksPath .githooks
echo "Git hooks enabled: core.hooksPath=.githooks"
echo "Run 'git config --get core.hooksPath' to verify."

