# Kickertool (Backend)

Kicker-Turnierverwaltung als reine REST-API: Turniere mit wechselnden Partnern (2 gegen 2), mehrere Tische in Wellen,
Einzelrangliste, Anmeldung über OIDC. Die UI liegt in einem eigenen Repo und arbeitet nur gegen diese API.
Fachliche Regeln, API und Architektur stehen in `README.md`, die Übergabe für UI-Entwicklung in `docs/UI-ÜBERGABE.md`.

Diese Datei gilt für alle Assistenten (Codex, Claude Code, ...). Eigenheiten einzelner Rechner stehen nicht hier.

## Arbeitsweise
- Bei Unklarheiten in den Anforderungen **nachfragen**, nicht selbst entscheiden. Getroffene Annahmen offen benennen.
- Sprache für Doku, Kommentare und Fehlermeldungen: Deutsch. Code-Bezeichner: Englisch.
- Änderungen mit Tests absichern. Vor dem Abschluss alles bauen: `./mvnw verify` (Windows: `.\mvnw.cmd verify`).

## Bauen und Testen
- JDK 21 oder neuer, Maven kommt über den Wrapper (`./mvnw`, Windows `.\mvnw.cmd`). Kompiliert wird mit `--release 21`.
- Einzelne Module: `./mvnw -pl kickertool-domain test`. Mit abhängigen Modulen `-am`, für Folgemodule vorher `install`.
- Nach Änderungen an `pom.xml` oder mehreren Modulen vor `spring-boot:run -pl kickertool-bootstrap` einmal alles
  installieren (`./mvnw -DskipTests install`), sonst liegt ein alter Eltern-POM im lokalen Repo und das Modul wird als
  ungültig gemeldet.
- `PostgresPersistenceTest` braucht Docker und wird ohne Docker übersprungen. Alle anderen Tests laufen ohne.

## Architektur-Regeln (von `ArchitectureTest` geprüft)
- Maven-Multi-Module, hexagonal: `domain` hängt von nichts außer dem JDK ab. `application` kennt nur `domain` und
  `@Transactional`.
- Adapter (`adapter-rest`, `adapter-persistence`, `adapter-events`) sprechen nur mit Ports (`application.port.*`),
  nie mit `application.service` und nie untereinander.
- Nur `bootstrap` verdrahtet (`UseCaseConfiguration`). Neue Use Cases: Port in `port.in`, Dienst in `service`, Bean dort.
- Das Turnier-Aggregat (`Tournament` samt `Round`, `Match`, `Participant`) liegt in einem Package, damit Mutatoren
  package-private bleiben. Zustandsänderungen nur über `Tournament`. Fachregeln gehören in die Domäne, nicht in die
  Controller (Beispiel: `Match.canEnterResult`, das die API als Flag meldet).

## Spring Boot 4
- Modulare Starter (`spring-boot-starter-webmvc`, `-security-oauth2-resource-server`, `-flyway`, ...), Jackson 3
  (`tools.jackson`), Test-Pakete z. B. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
  `org.springframework.boot.persistence.autoconfigure.EntityScan`.

## API-Vertrag (wichtig für die UI)
- springdoc (OpenAPI 3 + Swagger UI) im Modul `adapter-rest`. Konfiguration in `openapi/OpenApiConfiguration`,
  Einstellungen unter `springdoc.*` in `application.yml`.
- Neue Endpunkte mit `@Operation(summary, description)` und, wo nötig, `@Tag` beschreiben, DTO-Felder mit `@Schema`.
  Fehlerantworten (400/401/403/404/409) hängt ein `OperationCustomizer` automatisch an.
- **`docs/openapi.json` ist der eingecheckte Vertrag.** Ändert sich die API, schlägt `OpenApiSnapshotTest` fehl. Neu
  erzeugen: `./mvnw -pl kickertool-bootstrap test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true` und die Datei mit
  einchecken. `OpenApiDocumentationTest` prüft zusätzlich, dass alle Pfade beschrieben sind.
- Matches enthalten `mySide` und `permissions` (`canEnterResult`, `canConfirm`, `canReject`, `canDecide`) für den
  Aufrufer, damit Clients die Regeln nicht nachbauen. Die Antworten sind dadurch vom Aufrufer abhängig.
- CORS: Standardmäßig ist keine Browser-Herkunft erlaubt. Freigabe über `kickertool.cors.allowed-origins`
  (Umgebungsvariable `KICKERTOOL_CORS_ALLOWED_ORIGINS`, kommagetrennt, Muster wie `http://localhost:[*]` erlaubt).

## Lokaler Testmodus
- Modul `kickertool-local` (simulierter OIDC-Provider unter `/mock-oidc`, Testnutzer, Demo-Turnier), Spring-Profil
  `local`. Das Bootstrap-Modul bindet es nur mit dem Maven-Profil `local` zur Laufzeit ein, sonst nur als
  Test-Abhängigkeit. Start: `./scripts/run-local.sh` oder `.\scripts\run-local.ps1` (`-Port 9090` für einen anderen Port).
- Nichts aus `kickertool-local` darf in ein Produktions-Jar. `LocalProfileTest` prüft den Weg von Swagger UI und einer
  UI auf anderer Adresse: Discovery, Password-Flow, CORS, API-Aufruf.

## Docker und Helm
- `Dockerfile` (mehrstufig, Spring-Boot-Schichten, Nicht-Root-Benutzer 10001), `docker-compose.yml` (Profil `app` startet
  zusätzlich die App), Chart in `helm/kickertool` (App, PostgreSQL, Keycloak, eigene Templates ohne Subcharts).
- Das Image enthält `kickertool-local` und H2 nicht. Nach Änderungen am Dockerfile oder Chart:
  `docker build -t kickertool:0.1.0-SNAPSHOT .`, `helm lint helm/kickertool`, danach in einem Test-Namespace
  installieren und wieder entfernen.
- Token-Aussteller: `KICKERTOOL_OIDC_ISSUER_URI` ist die Browser-Adresse von Keycloak, die Schlüssel kommen über
  `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` aus dem Cluster. Beides muss zusammenpassen, sonst 401.
- Init-Container laufen als UID 10001 ohne passwd-Eintrag: `pg_isready` braucht `PGUSER`.

## Windows und PowerShell
- Auf Windows geht `localhost` zuerst an Programme auf `::`, die Docker-Portweiterleitung kann dadurch verdeckt werden
  (z. B. eine IDE-Instanz auf 8080).
- .NET-Dateifunktionen (`[System.IO.File]`) lösen relative Pfade gegen das Prozessverzeichnis auf, nicht gegen
  `Set-Location`. Absolute Pfade verwenden. Windows PowerShell 5.1 schreibt mit `-Encoding UTF8` ein BOM, das `javac`
  nicht mag: Quelldateien ohne BOM speichern.
- `Invoke-RestMethod -Method Post` ohne Body sendet einen Formular-Content-Type, `POST …/participants` antwortet dann
  mit 415. Mit `-ContentType 'application/json' -Body '{}'` aufrufen.
