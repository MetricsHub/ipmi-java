# ipmicli installer for Windows — https://metricshub.org/ipmi-java
#
# Copyright 2023 MetricsHub
# Distributed under the GNU Lesser General Public License, version 3 or
# later (LGPL-3.0-or-later); see <http://www.gnu.org/licenses/lgpl-3.0.html>.
#
# Usage (PowerShell):
#   irm https://metricshub.org/ipmi-java/get.ps1 | iex
#
# Environment variables:
#   IPMICLI_VERSION      Pin a specific release (e.g. "2.0.00"); default: latest.
#   IPMICLI_INSTALL_DIR  Installation directory; default: %LOCALAPPDATA%\ipmicli.
#
# The installer downloads the standalone jar of the requested IPMI Java Client
# release from GitHub, verifies its SHA-256 checksum, writes an ipmicli.cmd
# shim that locates a JRE (Java 8 or later) at run time, and adds the shim
# directory to the user PATH.

# The advertised `irm ... | iex` invocation evaluates this script in the
# caller's scope: the whole body runs in a script block so preference and
# helper variables cannot leak into the user's session.
& {

$ErrorActionPreference = 'Stop'

$releasesUrl = 'https://github.com/metricshub/ipmi-java/releases'
$version = if ($env:IPMICLI_VERSION) { $env:IPMICLI_VERSION } else { 'latest' }
$installDir = if ($env:IPMICLI_INSTALL_DIR) { $env:IPMICLI_INSTALL_DIR } else { Join-Path $env:LOCALAPPDATA 'ipmicli' }

# The user PATH entry must never depend on a working directory, so resolve
# relative, drive-relative (C:ipmicli), and root-relative (\ipmicli) overrides
# with GetFullPath, aligning the process working directory with the
# PowerShell location for the call (and restoring it right after).
$savedCwd = [Environment]::CurrentDirectory
[Environment]::CurrentDirectory = (Get-Location).ProviderPath
try {
    $installDir = [System.IO.Path]::GetFullPath($installDir)
} finally {
    [Environment]::CurrentDirectory = $savedCwd
}

$binDir = Join-Path $installDir 'bin'
$jarPath = Join-Path $installDir 'ipmi-java-standalone.jar'
$shimPath = Join-Path $binDir 'ipmicli.cmd'

# Releases publish a version-less "ipmi-java-standalone.jar" alias, which
# makes "latest" resolvable without any API call.
if ($version -eq 'latest') {
    $jarUrl = "$releasesUrl/latest/download/ipmi-java-standalone.jar"
} else {
    $jarUrl = "$releasesUrl/download/v$version/ipmi-java-$version-standalone.jar"
}

# --- Download the jar and verify its checksum --------------------------------

# The jar is downloaded and verified in the temporary directory first, so a
# failure leaves an existing installation untouched.
$tmpJar = Join-Path ([System.IO.Path]::GetTempPath()) "ipmi-java-standalone-$PID.jar"
Write-Host "Downloading $jarUrl ..."
try {
    Invoke-WebRequest -UseBasicParsing -Uri $jarUrl -OutFile $tmpJar
} catch {
    throw "Download failed: check that you are online and that the release exists (the command-line client ships with 2.0.00 and later)."
}

# Transient failures (Windows PowerShell can drop a reused connection) are
# retried before giving up.
$sumUrl = "$jarUrl.sha256"
for ($attempt = 1; $attempt -le 3; $attempt++) {
    try {
        $expectedSum = (Invoke-RestMethod -UseBasicParsing -Uri $sumUrl) -split '\s+' | Select-Object -First 1
        break
    } catch {
        if ($attempt -eq 3) {
            Remove-Item -Force $tmpJar
            throw "Could not download $sumUrl to verify the jar: $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 1
    }
}
$actualSum = (Get-FileHash -Algorithm SHA256 -Path $tmpJar).Hash
if ($actualSum -ne $expectedSum) {
    Remove-Item -Force $tmpJar
    throw "Checksum mismatch: expected $expectedSum, got $actualSum. Aborting."
}
Write-Host 'Checksum verified.'

# --- Install the jar and the shim -----------------------------------------------

New-Item -ItemType Directory -Force -Path $binDir | Out-Null
Move-Item -Force $tmpJar $jarPath

# The shim is a literal here-string so cmd metacharacters (%%, $PATH) survive.
# It locates the jar relative to its own directory (%~dp0), so the content is
# pure ASCII regardless of the install path (non-ASCII user names included)
# and the installation can be relocated as a whole.
$shim = @'
@echo off
rem ipmicli launcher - https://metricshub.org/ipmi-java
rem Locates a JRE (Java 8 or later) and runs the IPMI Java Client standalone jar.
rem Set IPMICLI_JAVA_HOME to force a specific Java installation.

setlocal
set "IPMICLI_JAR=%~dp0..\ipmi-java-standalone.jar"

rem Pass the launcher's own invocation name so the CLI's help names the
rem command the user actually typed instead of "java -jar ...".
set "IPMICLI_PROGRAM_NAME=%~n0"

if not exist "%IPMICLI_JAR%" (
    echo ERROR: %IPMICLI_JAR% not found; re-run the installer: 1>&2
    echo   irm https://metricshub.org/ipmi-java/get.ps1 ^| iex 1>&2
    exit /b 127
)

set "IPMICLI_JAVA="
if defined IPMICLI_JAVA_HOME call :try_java "%IPMICLI_JAVA_HOME%\bin\java.exe"
if not defined IPMICLI_JAVA if defined JAVA_HOME call :try_java "%JAVA_HOME%\bin\java.exe"
if not defined IPMICLI_JAVA for %%J in (java.exe) do if not "%%~$PATH:J" == "" call :try_java "%%~$PATH:J"

if not defined IPMICLI_JAVA (
    echo ERROR: no suitable Java Runtime Environment found ^(ipmicli requires Java 8 or later^). 1>&2
    echo Install one from https://adoptium.net, or set IPMICLI_JAVA_HOME. 1>&2
    exit /b 127
)

"%IPMICLI_JAVA%" -jar "%IPMICLI_JAR%" %*
exit /b %ERRORLEVEL%

rem A candidate qualifies if it runs and does not report a 1.0-1.7 version
rem ("1.8" is Java 8 in the legacy version scheme). The findstr result is
rem read through errorlevel in a separate statement: an exit /b appended to
rem the pipeline would run in the pipe's child cmd, not in this subroutine.
:try_java
if not exist "%~1" exit /b 0
"%~1" -version >nul 2>&1 || exit /b 0
"%~1" -version 2>&1 | findstr /r /c:"version .1\.[0-7]\." >nul
if not errorlevel 1 exit /b 0
set "IPMICLI_JAVA=%~1"
exit /b 0
'@
# cmd only finds "call :label" targets in CRLF files, and the here-string
# carries whatever line endings this script was downloaded with: normalize.
$shim = ($shim -replace "`r`n", "`n") -replace "`n", "`r`n"
$shim | Set-Content -Path $shimPath -Encoding ASCII

Write-Host "Installed the IPMI Java Client jar to $jarPath"
Write-Host "Installed the ipmicli launcher to $shimPath"

# --- PATH registration -----------------------------------------------------------

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$onPath = ($userPath -split ';' | Where-Object { $_ -eq $binDir }).Count -gt 0
if (-not $onPath) {
    $newPath = if ($userPath) { "$userPath;$binDir" } else { $binDir }
    [Environment]::SetEnvironmentVariable('Path', $newPath, 'User')
    Write-Host "Added $binDir to your user PATH."
    Write-Host "Open a new terminal, then run 'ipmicli --help' to get started."
} else {
    Write-Host "Run 'ipmicli --help' to get started."
}

}
