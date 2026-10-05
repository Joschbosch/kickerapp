# Kickertool

Kicker-Turnierverwaltung als reine REST-API (keine UI). Fachliche Regeln, API und Architektur stehen in `README.md`.

## Arbeitsweise
- Bei Unklarheiten in den Anforderungen **nachfragen**, nicht selbst entscheiden. Getroffene Annahmen offen benennen.
- Sprache für Doku, Kommentare und Fehlermeldungen: Deutsch. Code-Bezeichner: Englisch.

## Bauen
- Maven Wrapper nutzen (`.\mvnw.cmd` unter Windows). Auf dieser Maschine ist kein JDK im PATH: vorher
  `$env:JAVA_HOME="$env:USERPROFILE\.jdks\openjdk-26.0.1"` setzen. Kompiliert wird mit `--release 21`.
- Einzelne Module: `.\mvnw.cmd -pl kickertool-domain test`. Mit abhängigen Modulen `-am`, für Folgemodule vorher `install`.
- Nach Änderungen an `pom.xml` oder mehreren Modulen vor `spring-boot:run -pl kickertool-bootstrap` einmal alles installieren (`.\mvnw.cmd -DskipTests install`), sonst liegt ein alter Eltern-POM im lokalen Repo und das Modul wird als ungültig gemeldet.
- Docker (Rancher Desktop) läuft meist nicht: `PostgresPersistenceTest` wird dann übersprungen.

## Architektur-Regeln (von `ArchitectureTest` geprüft)
- `domain` hängt von nichts außer dem JDK ab. `application` kennt nur `domain` und `@Transactional`.
- Adapter sprechen nur mit Ports (`application.port.*`), nie mit `application.service`, und nie untereinander.
- Nur `bootstrap` verdrahtet (`UseCaseConfiguration`). Neue Use Cases: Port in `port.in`, Dienst in `service`, Bean dort.
- Das Turnier-Aggregat (`Tournament` samt `Round`, `Match`, `Participant`) liegt in einem Package, damit Mutatoren
  package-private bleiben. Zustandsänderungen nur über `Tournament`.

## Spring Boot 4
- Modulare Starter (`spring-boot-starter-webmvc`, `-security-oauth2-resource-server`, `-flyway`, ...), Jackson 3
  (`tools.jackson`), Test-Pakete z. B. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
  `org.springframework.boot.persistence.autoconfigure.EntityScan`.

## API-Doku
- springdoc (OpenAPI 3 + Swagger UI) im Modul `adapter-rest`. Konfiguration in `openapi/OpenApiConfiguration`, Einstellungen unter `springdoc.*` in `application.yml`.
- Neue Endpunkte mit `@Operation(summary, description)` und, wo nötig, `@Tag` beschreiben, DTO-Felder mit `@Schema`. Fehlerantworten (400/401/403/404/409) hängt ein `OperationCustomizer` automatisch an. `OpenApiDocumentationTest` prüft, dass alle Pfade in der Doku stehen.

## Lokaler Testmodus
- Modul `kickertool-local` (simulierter OIDC-Provider unter `/mock-oidc`, Testnutzer, Demo-Turnier), Spring-Profil `local`. Das Bootstrap-Modul bindet es nur mit dem Maven-Profil `local` zur Laufzeit ein (sonst nur als Test-Abhängigkeit). Start: `.\scripts\run-local.ps1`.
- `LocalProfileTest` prüft den Weg von Swagger UI: Discovery, Password-Flow, API-Aufruf. Nichts aus `kickertool-local` darf in ein Produktions-Jar.

## Docker und Helm
- `Dockerfile` (mehrstufig, Spring-Boot-Schichten, Nicht-Root-Benutzer 10001), `docker-compose.yml` (Profil `app` startet zusätzlich die App), Chart in `helm/kickertool` (App, PostgreSQL, Keycloak, eigene Templates ohne Subcharts).
- Das Image enthält `kickertool-local` und H2 nicht. Nach Änderungen am Dockerfile oder Chart: `docker build -t kickertool:0.1.0-SNAPSHOT .`, `helm lint helm/kickertool`, danach in einem Test-Namespace installieren und wieder entfernen.
- Token-Aussteller: `KICKERTOOL_OIDC_ISSUER_URI` ist die Browser-Adresse von Keycloak, die Schlüssel kommen über `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` aus dem Cluster. Beides muss zusammenpassen, sonst 401.
- Init-Container laufen als UID 10001 ohne passwd-Eintrag: `pg_isready` braucht `PGUSER`.
- Auf Windows geht `localhost` zuerst an Programme auf `::`, die Docker-Portweiterleitung kann dadurch verdeckt werden (z. B. IDE-Instanz auf 8080).
- PowerShell: .NET-Dateifunktionen (`[System.IO.File]`) lösen relative Pfade gegen das Prozessverzeichnis auf, nicht gegen `Set-Location`. Absolute Pfade verwenden.
