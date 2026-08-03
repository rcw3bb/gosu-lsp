#Requires -Version 5.1
<#
.SYNOPSIS
    Full pipeline: build the server shadow JAR, copy it to the client, then package the VS Code extension as a VSIX.
.DESCRIPTION
    1. Runs `:server:copyServerJar` via Gradle (compiles Java, produces the fat JAR, copies it to client/server/).
    2. Installs Node dependencies in client/.
    3. Packages the extension with @vscode/vsce (the vscode:prepublish hook compiles TypeScript automatically).
    The resulting .vsix file is written to client/.
#>

$ErrorActionPreference = 'Stop'

Set-Location $PSScriptRoot

Write-Host '==> Building server JAR and copying to client/server/ ...' -ForegroundColor Cyan
& .\gradlew.bat :server:copyServerJar
if ($LASTEXITCODE -ne 0) { throw "Gradle :server:copyServerJar failed (exit $LASTEXITCODE)." }

Write-Host '==> Installing Node dependencies ...' -ForegroundColor Cyan
Copy-Item -Path "$PSScriptRoot\README.md" -Destination "$PSScriptRoot\client\README.md" -Force
Push-Location client
try {
    & npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install failed (exit $LASTEXITCODE)." }

    Write-Host '==> Packaging VS Code extension as VSIX ...' -ForegroundColor Cyan
    & npx --yes @vscode/vsce package
    if ($LASTEXITCODE -ne 0) { throw "vsce package failed (exit $LASTEXITCODE)." }

    $vsix = Get-ChildItem -Filter '*.vsix' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($vsix) {
        Write-Host "==> VSIX created: $($vsix.FullName)" -ForegroundColor Green
    }
} finally {
    Remove-Item -Path "$PSScriptRoot\client\README.md" -ErrorAction SilentlyContinue
    Pop-Location
}
