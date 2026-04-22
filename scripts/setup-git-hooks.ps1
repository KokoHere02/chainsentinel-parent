$ErrorActionPreference = "Stop"

git config core.hooksPath .githooks
Write-Output "Git hooks enabled: core.hooksPath=.githooks"
Write-Output "Run 'git config --get core.hooksPath' to verify."

