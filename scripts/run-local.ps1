# Startet die App lokal ohne Docker: H2 im Speicher und simulierter OIDC-Provider (Profil "local").
# Danach: http://localhost:8080/swagger-ui.html
Set-Location (Split-Path -Parent $PSScriptRoot)

# Kein JDK im PATH? Dann das neueste aus ~/.jdks nehmen (z. B. von IntelliJ geladen).
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '.*' } | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName; Write-Host "JAVA_HOME = $($env:JAVA_HOME)" }
}

# Alle Module installieren, damit das Bootstrap-Modul die aktuellen Stände findet
.\mvnw.cmd -q -DskipTests install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

.\mvnw.cmd -pl kickertool-bootstrap -Plocal spring-boot:run
