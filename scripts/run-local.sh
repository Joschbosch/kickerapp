#!/usr/bin/env sh
# Startet die App lokal ohne Docker: H2 im Speicher und simulierter OIDC-Provider (Profil "local").
# Danach: http://localhost:8080/swagger-ui.html
#
# Anderer Port:  PORT=9090 ./scripts/run-local.sh
set -e
cd "$(dirname "$0")/.."
PORT="${PORT:-8080}"

# Java 21 oder neuer?
if ! command -v java >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    echo "Java wurde nicht gefunden. Bitte JDK 21 oder neuer installieren und JAVA_HOME setzen." >&2
    exit 1
fi
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
MAJOR=$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)
if [ -z "$MAJOR" ] || [ "$MAJOR" -lt 21 ]; then
    echo "Gefunden wurde Java ${MAJOR:-unbekannt}. Gebraucht wird Java 21 oder neuer." >&2
    exit 1
fi

# Port frei? (nur prüfen, wenn nc vorhanden ist)
if command -v nc >/dev/null 2>&1 && nc -z localhost "$PORT" >/dev/null 2>&1; then
    echo "Port $PORT ist belegt (läuft die App schon, z. B. in der IDE?). Beenden oder PORT=9090 setzen." >&2
    exit 1
fi

# Alle Module installieren, damit das Bootstrap-Modul die aktuellen Stände findet
./mvnw -q -DskipTests install

./mvnw -pl kickertool-bootstrap -Plocal spring-boot:run "-Dspring-boot.run.arguments=--server.port=$PORT"
