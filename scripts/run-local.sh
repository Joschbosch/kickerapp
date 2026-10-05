#!/usr/bin/env sh
# Startet die App lokal ohne Docker: H2 im Speicher und simulierter OIDC-Provider (Profil "local").
# Danach: http://localhost:8080/swagger-ui.html
set -e
cd "$(dirname "$0")/.."

# Alle Module installieren, damit das Bootstrap-Modul die aktuellen Stände findet
./mvnw -q -DskipTests install

./mvnw -pl kickertool-bootstrap -Plocal spring-boot:run
