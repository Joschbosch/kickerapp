# Startet die App lokal ohne Docker: H2 im Speicher und simulierter OIDC-Provider (Profil "local").
# Danach: http://localhost:8080/swagger-ui.html
#
# Anderer Port:  .\scripts\run-local.ps1 -Port 9090
param([int]$Port = 8080)

Set-Location (Split-Path -Parent $PSScriptRoot)

# Java 21 oder neuer finden: JAVA_HOME, sonst PATH, sonst das neueste JDK aus ~/.jdks (z. B. von IntelliJ geladen)
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '.*' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName; Write-Host "JAVA_HOME = $($env:JAVA_HOME)" }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$versionText = (& $java -version 2>&1 | Out-String) 2>$null
if ($versionText -notmatch 'version "(\d+)') {
    Write-Error "Java wurde nicht gefunden. Bitte JDK 21 oder neuer installieren und JAVA_HOME setzen."
    exit 1
}
if ([int]$Matches[1] -lt 21) {
    Write-Error "Gefunden wurde Java $($Matches[1]). Gebraucht wird Java 21 oder neuer."
    exit 1
}

if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    Write-Error "Port $Port ist belegt (läuft die App schon, z. B. in der IDE?). Beenden oder einen anderen Port wählen: -Port 9090"
    exit 1
}

# Alle Module installieren, damit das Bootstrap-Modul die aktuellen Stände findet
.\mvnw.cmd -q -DskipTests install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

.\mvnw.cmd -pl kickertool-bootstrap -Plocal spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
