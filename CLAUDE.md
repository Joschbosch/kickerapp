# Kickertool

Kicker-Turnierverwaltung als reine REST-API (keine UI). Fachliche Regeln, API und Architektur stehen in `README.md`.

## Arbeitsweise
- Bei Unklarheiten in den Anforderungen **nachfragen**, nicht selbst entscheiden. Getroffene Annahmen offen benennen.
- Sprache für Doku, Kommentare und Fehlermeldungen: Deutsch. Code-Bezeichner: Englisch.

## Bauen
- Maven Wrapper nutzen (`.\mvnw.cmd` unter Windows). Auf dieser Maschine ist kein JDK im PATH: vorher
  `$env:JAVA_HOME="$env:USERPROFILE\.jdks\openjdk-26.0.1"` setzen. Kompiliert wird mit `--release 21`.
- Einzelne Module: `.\mvnw.cmd -pl kickertool-domain test`. Mit abhängigen Modulen `-am`, für Folgemodule vorher `install`.
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
