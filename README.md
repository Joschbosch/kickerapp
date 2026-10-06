# Kickertool

Backend für Kicker-Turniere (2 gegen 2) mit wechselnden Partnern in jeder Runde. Nur REST-API, keine UI.
Spring Boot 4.1, Java 21, hexagonale Architektur, Anmeldung über einen beliebigen OIDC-Provider (Keycloak als Standard).

## Fachliche Regeln

**Ablauf**
1. Der Admin plant ein Turnier (Name, Datum, Konfiguration). Spieler melden sich an.
2. Am Turniertag konfiguriert der Admin, bis er das Turnier **startet**. Anmelden ist auch danach noch möglich, wer später kommt, spielt ab der nächsten Runde mit.
3. **Nur der Admin startet Runden.** Eine neue Runde geht erst, wenn alle Ergebnisse der letzten bestätigt sind.
4. Eine Runde: Alle aktiven Spieler spielen genau ein Match 2 gegen 2. Die Teamzuteilung ist austauschbar (`TeamAssignmentStrategy`), eingebaut ist ein Schweizer DYP (`SwissDypTeamAssignmentStrategy`):
   - Die ersten `randomRounds` Runden (konfigurierbar, Standard 2) werden **komplett zufällig** ausgelost.
   - Danach werden die Spieler nach Rang in **Blöcke zu je 4** aufgeteilt (Rang 1 bis 4, 5 bis 8, ...), Spieler mit gleichem Rang vorher gemischt. Innerhalb eines Blocks wird gemischt und in zwei Teams geteilt. Der beste Block kommt in der Auslosung zuerst dran. Fehlen Spieler (Dummys), bildet ein unvollständiger Block die Lücke, der in der Auslosung zuletzt dran kommt. Dafür werden die Spieler gewählt, die bisher **am seltensten in einem Match mit Dummys** standen (Rotation), bei Gleichstand die niedrigsten Ränge, in Zufallsrunden zufällig.
   - **Partnerregel in allen Runden:** Wer in der letzten Runde Partner war, soll es nicht gleich wieder sein. Das ist ein sehr starker Wunsch, er wird nur verletzt, wenn keine andere Aufteilung geht. Innerhalb eines Blocks gibt es immer eine passende Aufteilung, die Regel wird mit dieser Strategie also in der Praxis nie gebrochen.
5. Fehlen Spieler zur vollen Vierergruppe, springen **Dummys** ein (maximal 3 pro Runde). Das System schlägt als Einspringer einen aktiven Teilnehmer vor, der gerade nicht an einem Tisch steht und in der Rangliste nahe den anderen Spielern des Matches liegt. Dummys sind nicht im System registriert, bekommen **keine Punkte** und zählen in keiner Rangliste. Ist niemand verfügbar, spielt ein Dritter aus dem Publikum.
6. **Tische in Wellen:** Es gibt `tableCount` Tische. Die ersten Matches der Auslosung belegen die Tische, die übrigen warten. Sobald für alle Matches am Tisch ein Ergebnis eingetragen ist, rückt die nächste Welle nach. Die Tischzahl kann jederzeit geändert werden (gilt ab der nächsten Welle).
7. **Ergebnis:** Ein Match geht bis `goalLimit` Tore (Standard 10) oder `matchMinutes` Minuten (Standard 5), nur ein Satz. Ein Spieler des einen Teams trägt das Ergebnis ein, ein Spieler des Gegnerteams bestätigt. Lehnt das Gegnerteam ab, entscheidet der Admin. Der Admin kann Ergebnisse jederzeit festlegen und nachträglich korrigieren. Das System prüft, dass kein Team mehr Tore als das Limit hat und nicht beide das Limit erreichen (Unentschieden nach Zeitablauf ist möglich).
8. **Wertung (Einzelwertung):** Sieg 3, Unentschieden 1, Niederlage 0 Punkte, alles konfigurierbar. Sortierung: Punkte, dann Tordifferenz, dann erzielte Tore. Spieler mit identischen Werten teilen sich den Rang. Pro Spieler: Rang, Punkte, Spiele, Siege, Unentschieden, Niederlagen, Tore, Gegentore, Tordifferenz. Es zählen nur bestätigte Ergebnisse.
9. **Pause und Ausscheiden:** Pausieren wirkt ab der nächsten Runde (die laufende wird normal beendet), Wiedereinstieg ist möglich. Ausgeschiedene sind als `WITHDRAWN` markiert, behalten ihre Punkte und kommen nicht zurück.
10. Die Rundenzahl ist offen (`plannedRounds` ist nur eine Anzeige). Der Admin beendet das Turnier.

## Architektur

Maven-Multi-Module, Abhängigkeiten zeigen immer nach innen. Die Regeln prüft `ArchitectureTest` (ArchUnit).

```
kickertool-domain               reine Fachlogik: Turnier, Runden, Matches, Rangliste, Zuteilung (kein Framework)
kickertool-application          Use Cases (port.in), Ports (port.out), Anwendungsdienste
kickertool-adapter-rest         eingehend: REST-API, OIDC-Absicherung als Resource Server, SSE
kickertool-adapter-persistence  ausgehend: JPA + PostgreSQL + Flyway
kickertool-adapter-events       ausgehend: Verteilung der Live-Update-Events (In-Memory, austauschbar)
kickertool-local                nur für lokale Tests: simulierter OIDC-Provider, Testnutzer, Demo-Turnier (nur mit Profil "local")
Dockerfile, docker-compose.yml  Container-Image und Entwicklungsumgebung mit Docker
helm/kickertool                 Helm-Chart: App, PostgreSQL und Keycloak im Kubernetes-Cluster
docs/                           Übergabe für UI-Entwicklung, API-Vertrag (openapi.json), Vorlage für das UI-Repo
AGENTS.md, CLAUDE.md            Projektregeln für KI-Assistenten (Codex, Claude Code); CLAUDE.md verweist auf AGENTS.md
kickertool-bootstrap            startbare Spring-Boot-App, verdrahtet alles
```

Das Turnier ist ein Aggregat (`Tournament`). Änderungen laufen in einer Transaktion, die das Turnier sperrt, weil viele Handys gleichzeitig Ergebnisse eintragen. Die Rangliste wird immer aus den bestätigten Matches berechnet, Korrekturen des Admins wirken also sofort.

## Anmeldung (OIDC)

Registrierung und Login übernimmt der OIDC-Provider. Die API ist ein **Resource Server** und prüft nur die Bearer-Tokens (JWT). Beim ersten Aufruf mit einem gültigen Token wird der Spieler automatisch angelegt (über `sub`, Anzeigename aus `name`, sonst `preferred_username`, sonst `email`).

| Einstellung | Umgebungsvariable | Standard |
|---|---|---|
| Aussteller des Providers | `KICKERTOOL_OIDC_ISSUER_URI` | `http://localhost:8180/realms/kickertool` |
| Claim mit den Rollen | `KICKERTOOL_ADMIN_CLAIM` | `realm_access.roles` |
| Rolle, die zum Admin macht | `KICKERTOOL_ADMIN_ROLE` | `kicker-admin` |
| Datenbank | `KICKERTOOL_DB_URL`, `_USER`, `_PASSWORD` | lokales Postgres, `kickertool` |
| Swagger UI und OpenAPI-Doku | `KICKERTOOL_OPENAPI_ENABLED` | `true` (in Produktion `false`) |
| Erlaubte Browser-Adressen (CORS), kommagetrennt | `KICKERTOOL_CORS_ALLOWED_ORIGINS` | leer = nur dieselbe Adresse. Muster wie `https://*.example.com` oder `http://localhost:[*]` sind erlaubt |

Der Claim-Pfad darf verschachtelt sein (`resource_access.<client>.roles`), eine Zeichenkette wird an Leerzeichen getrennt. Damit läuft die App mit jedem OIDC-Provider, der JWT-Access-Tokens ausstellt.

## Lokal starten

Voraussetzung: Docker (Postgres + Keycloak) und JDK 21 oder neuer. Maven kommt über den Wrapper.

```bash
docker compose up -d
./mvnw -pl kickertool-bootstrap spring-boot:run
```

Unter Windows `.\mvnw.cmd` verwenden. Der Realm `kickertool` bringt den Client `kickertool-app` und drei Testnutzer mit (`admin`/`admin` mit Admin-Rolle, `anna`/`anna`, `ben`/`ben`) und erlaubt Selbstregistrierung. Token holen:

```bash
curl -s -d "client_id=kickertool-app" -d "grant_type=password" -d "username=anna" -d "password=anna" \
  http://localhost:8180/realms/kickertool/protocol/openid-connect/token
```

Danach `Authorization: Bearer <access_token>` an jede Anfrage hängen. Die Testnutzer und `redirectUris: *` gelten nur für die lokale Entwicklung.

## API ausprobieren (Swagger UI)

Mit laufender App (und Keycloak) gibt es eine interaktive Dokumentation:

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |

**Anmelden in Swagger UI:** Oben rechts **Authorize** wählen.
- `oidc`: Login über Keycloak. Mit dem Flow *password* genügt `anna` / `anna` (oder `admin` / `admin` für Admin-Endpunkte) und die `client_id` `kickertool-app` ohne Secret. Das Token wird gemerkt, auch nach dem Neuladen der Seite.
- `bearerAuth`: ein fertiges Access-Token einfügen (siehe Token holen oben).

Dann bei einem Endpunkt **Try it out**. Typischer Ablauf zum Ausprobieren: als `admin` `POST /api/tournaments` (Turnier planen), als `anna` und `ben` `POST …/participants` (anmelden), als `admin` `…/start` und `…/rounds`, dann `GET …/matches/mine`. Mit einem zweiten Browserfenster oder einem Token pro Nutzer lassen sich Ergebnis eintragen und bestätigen nachspielen.

Der Live-Update-Stream (`…/events`) lässt sich in Swagger UI nicht darstellen, dafür `curl -N -H "Authorization: Bearer <token>" http://localhost:8080/api/tournaments/<id>/events` nutzen.

Die Doku ist öffentlich lesbar, die Aufrufe der API selbst brauchen ein Token. In Produktion mit `KICKERTOOL_OPENAPI_ENABLED=false` abschalten.
## Lokal ohne Docker (H2 und simulierter Keycloak)

Zum schnellen Ausprobieren braucht es weder Postgres noch Keycloak. Das Maven-Profil `local` startet die App mit H2 im Speicher und einem **simulierten OIDC-Provider** in der App selbst (`/mock-oidc`), dazu ein Demo-Turnier:

```powershell
.\scripts\run-local.ps1
```

(Linux/macOS: `./scripts/run-local.sh`.) Anderer Port: `.\scripts\run-local.ps1 -Port 9090` bzw. `PORT=9090 ./scripts/run-local.sh`. Das Skript prüft Java 21 und den Port, installiert alle Module und startet `.\mvnw.cmd -pl kickertool-bootstrap -Plocal spring-boot:run`. In IntelliJ: Maven-Profil `local` aktivieren und die Spring-Profile `local` setzen.

Danach ist alles unter http://localhost:8080/swagger-ui.html erreichbar:

- **Anmelden:** **Authorize**, dann `oidc` (Password-Flow), Benutzername = Passwort. Die `client_id` ist vorbelegt, ein Secret ist nicht nötig.
- **Benutzer:** `admin` (Admin-Rechte) und die Spieler `anna`, `ben`, `clara`, `david`, `emma`, `felix`, `greta`, `hans`, `ida`, `jonas`.
- **Demo-Turnier:** "Demo-Turnier" mit allen 10 Spielern angemeldet, 3 Tische, noch nicht gestartet. Als `admin` `…/start` und `…/rounds` aufrufen, dann als Spieler `…/matches/mine`, Ergebnis eintragen und als Gegner bestätigen. Für den Wechsel zwischen Spielern erneut **Authorize** (erst **Logout**).
- **UI auf anderer Adresse:** Im lokalen Modus ist CORS für jeden lokalen Port freigegeben (`http://localhost:[*]`), auch am simulierten Login. Eine UI auf Vite, Angular oder Next kann also direkt gegen die App arbeiten.
- **Tokens per Hand:** `curl -d "grant_type=password&username=anna&password=anna" http://localhost:8080/mock-oidc/token`
- Die Datenbank liegt im Speicher, nach jedem Neustart ist alles frisch. Die Tokens gelten nur bis zum nächsten Neustart.

**Nur lokal!** Der simulierte Provider lässt jeden als Admin herein. Er steckt im Modul `kickertool-local`, das nur mit dem Maven-Profil `local` und dem Spring-Profil `local` aktiv wird und nie im Produktions-Jar landet.
## Docker und Kubernetes

### Container-Image

```bash
docker build -t kickertool:0.1.0-SNAPSHOT .
```

Mehrstufiger Build: Maven baut die App (ohne Tests, die laufen in der CI), das Spring-Boot-Jar wird in Schichten zerlegt (Abhängigkeiten ändern sich selten, das spart beim erneuten Bau und Übertragen), und das Laufzeit-Image enthält nur ein JRE 21. Es läuft ohne Root (UID 10001), richtet den Speicher nach dem Limit des Containers aus (`JAVA_TOOL_OPTIONS`) und enthält weder den simulierten Login noch H2. Gesund ist die App, wenn `/actuator/health/liveness` bzw. `/actuator/health/readiness` melden. Konfiguriert wird sie über die Umgebungsvariablen aus der Tabelle oben, zusätzlich `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, falls der Aussteller aus dem Container nicht erreichbar ist (siehe unten).

### Docker Compose

```bash
docker compose --profile app up -d --build    # App, PostgreSQL und Keycloak
docker compose up -d                          # nur PostgreSQL und Keycloak, die App läuft dann in der IDE
```

Danach Swagger UI unter http://localhost:8080/swagger-ui.html, Login mit dem Keycloak-Realm (`anna`/`anna`, `admin`/`admin`). Die Ports 8080, 5432 und 8180 müssen frei sein. **Windows:** Läuft die App zusätzlich in der IDE auf 8080, antwortet auf `localhost:8080` diese statt des Containers. Dann zuerst die IDE-Instanz beenden.

Der Aussteller im Token ist die Adresse, unter der der Browser Keycloak erreicht (`http://localhost:8180/...`). Aus dem App-Container ist Keycloak aber unter `keycloak:8180` erreichbar, deshalb holt die App die Schlüssel von dort (`JWK_SET_URI`) und prüft den Aussteller gegen die Browser-Adresse. Dasselbe Muster nutzt das Helm-Chart.

### Helm-Chart (Kubernetes)

`helm/kickertool` bringt alles mit: die App, eine PostgreSQL-Instanz (StatefulSet mit Datenträger) und Keycloak mit importiertem Realm. Keine fremden Subcharts. Zum Ausprobieren auf einem lokalen Cluster (Rancher Desktop, kind, minikube):

```bash
docker build -t kickertool:0.1.0-SNAPSHOT .          # das Image muss dem Cluster bekannt sein (bei Rancher Desktop mit dockerd automatisch)
helm install kt helm/kickertool -n kickertool --create-namespace -f helm/kickertool/values-local.yaml
kubectl -n kickertool port-forward svc/kt-kickertool-keycloak 8180:8080    # Terminal 1
kubectl -n kickertool port-forward svc/kt-kickertool 8080:8080             # Terminal 2
```

Dann http://localhost:8080/swagger-ui.html, Anmeldung mit `anna`/`anna` (Password-Flow, nur wegen `values-local.yaml`). Aufräumen: `helm uninstall kt -n kickertool` und `kubectl delete namespace kickertool` (löscht auch den Datenträger). `helm test kt -n kickertool` prüft die Erreichbarkeit der App.

**Wichtige Einstellungen** (alle in `values.yaml` beschrieben):

| Wert | Bedeutung |
|---|---|
| `image.repository`, `image.tag` | Das Image. `tag` leer = Version des Charts |
| `keycloak.hostname` | Externe Adresse von Keycloak, wie der Browser sie aufruft. Daraus wird der Aussteller (`iss`) im Token. Muss zur echten Adresse passen, sonst weist die App Tokens ab |
| `keycloak.redirectUris`, `webOrigins` | Erlaubte Rückleitungen nach dem Login (z. B. Swagger UI, später die App) |
| `ingress.*` | Zugang von außen mit `host` für die App und `keycloakHost` für Keycloak, dazu `keycloak.proxyHeaders: xforwarded` |
| `postgresql.enabled=false` + `database.*` | Eigene oder verwaltete Datenbank statt der eingebauten |
| `keycloak.enabled=false` + `oidc.issuerUri` | Eigener OIDC-Provider statt Keycloak |
| `app.openapi` | Swagger UI und API-Doku an oder aus |
| `app.corsAllowedOrigins` | Browser-Adressen einer UI auf anderer Adresse (CORS), z. B. `https://turnier.example.com` |

**Admin-Rechte** bekommt, wem in Keycloak die Realm-Rolle `kicker-admin` zugewiesen ist (Admin-Konsole unter `keycloak.hostname`, Passwort steht im Secret, siehe Ausgabe von `helm install`).

**Für eine echte Umgebung:** HTTPS über den Ingress und `keycloak.sslRequired: external`, `demoUsers` und `directAccessGrants` aus, `redirectUris` ohne Platzhalter, Passwörter über `existingSecret` oder zufällig erzeugen lassen (leere Werte), `app.openapi: false`. Die eingebaute PostgreSQL ist eine einfache Einzelinstanz ohne Backup, besser eine verwaltete Datenbank mit `postgresql.enabled=false`. `replicaCount` bei 1 lassen, weil die Live-Updates im Speicher der Instanz verteilt werden. Mehrere Replikas bräuchten einen anderen `TournamentEventBus`-Adapter (z. B. Redis).

**Geprüft:** Image-Bau, Compose-Stack mit echtem Keycloak und PostgreSQL (Realm-Import, Login, Admin-Rolle, 401/403), Helm-Lint und Server-Dry-Run, Installation auf einem k3s-Cluster mit Login, Turnier anlegen und Anmelden, Neustart von PostgreSQL ohne Datenverlust, `helm test`. **Nicht geprüft:** Ingress, TLS und ein Betrieb hinter einem Reverse Proxy.
## Für die UI-Entwicklung

Die UI liegt in einem eigenen Repo. Alles, was dafür nötig ist, steht in [`docs/ui-uebergabe.md`](docs/ui-uebergabe.md):
Backend lokal starten, Anmeldung, Zustände, Live-Updates, bekannte Lücken und offene Entscheidungen. Dazu:

- [`docs/openapi.json`](docs/openapi.json) ist der eingecheckte **API-Vertrag**. Daraus lassen sich Clients erzeugen oder
  ein Mock-Server starten. `OpenApiSnapshotTest` hält die Datei aktuell (Neuerzeugen: siehe `AGENTS.md`).
- [`docs/ui-vorlage/AGENTS.md`](docs/ui-vorlage/AGENTS.md) ist eine Vorlage für die Projektregeln im UI-Repo, für Codex und
  Claude Code.
- Die Matches enthalten für den Aufrufer berechnete `permissions` (`canEnterResult`, `canConfirm`, `canReject`,
  `canDecide`) und `mySide`, damit die UI die Regeln nicht nachbauen muss.
## REST-API

Alle Endpunkte liegen unter `/api` und verlangen ein Token. Fehler kommen als `application/problem+json`:
400 ungültige Eingabe, 401 kein Token, 403 keine Berechtigung, 404 nicht gefunden, 409 passt nicht zum aktuellen Stand.
Als Spieler-ID kann überall `me` stehen.

| Methode und Pfad | Wer | Zweck |
|---|---|---|
| `GET /api/me` | alle | eigenes Profil (legt den Spieler beim ersten Aufruf an), `admin`-Flag |
| `GET /api/players` | Admin | alle bekannten Spieler |
| `GET /api/tournaments` | alle | Turnierliste |
| `POST /api/tournaments` | Admin | Turnier planen (`name`, `date`, optional `config`) |
| `GET /api/tournaments/{id}` | alle | Turnier mit Konfiguration, Teilnehmern, aktueller Runde |
| `PUT /api/tournaments/{id}` | Admin | Name und Datum ändern |
| `PUT /api/tournaments/{id}/config` | Admin | Konfiguration ersetzen, auch während des Turniers |
| `POST /api/tournaments/{id}/start` | Admin | Turnier starten |
| `POST /api/tournaments/{id}/rounds` | Admin | nächste Runde auslosen |
| `POST /api/tournaments/{id}/finish` | Admin | Turnier beenden |
| `GET /api/tournaments/{id}/ranking` | alle | Rangliste |
| `GET /api/tournaments/{id}/rounds` | alle | alle Runden mit Matches |
| `GET /api/tournaments/{id}/rounds/current` | alle | aktuelle Runde |
| `POST /api/tournaments/{id}/participants` | Spieler / Admin | anmelden (Body optional `{"playerId": …}`, nur Admin für andere) |
| `DELETE /api/tournaments/{id}/participants/{playerId}` | Spieler / Admin | abmelden, nur vor dem Start |
| `PUT /api/tournaments/{id}/participants/{playerId}/status` | Spieler / Admin | `PAUSED`, `ACTIVE` oder `WITHDRAWN` |
| `GET /api/tournaments/{id}/matches/mine` | alle | meine Matches (auch als Einspringer) |
| `GET /api/tournaments/{id}/matches/{matchId}` | alle | ein Match |
| `POST …/matches/{matchId}/result-proposal` | Spieler am Tisch | Ergebnis eintragen (`goalsA`, `goalsB`) |
| `POST …/matches/{matchId}/result-proposal/confirmation` | Gegnerteam | Ergebnis bestätigen |
| `POST …/matches/{matchId}/result-proposal/rejection` | Gegnerteam | Ergebnis ablehnen, der Admin entscheidet |
| `PUT …/matches/{matchId}/result` | Admin | Ergebnis festlegen oder korrigieren |
| `GET /api/tournaments/{id}/events` | alle | Live-Updates als Server-Sent Events |

**Live-Updates:** Der SSE-Stream sendet Hinweise (`TOURNAMENT_CHANGED`, `PARTICIPANTS_CHANGED`, `ROUND_STARTED`, `MATCHES_CHANGED`, `RANKING_CHANGED`) ohne Nutzdaten. Clients laden danach den neuen Stand über die normalen Endpunkte. Der Stream braucht den `Authorization`-Header, die Browser-`EventSource` kann das nicht, native Apps und `fetch`-basierte Clients schon. Die Verteilung läuft über den Port `TournamentEventBus`, der In-Memory-Adapter gilt für eine einzelne Instanz und lässt sich z. B. durch Redis ersetzen.

## Bauen und Testen

```bash
./mvnw verify
```

- Domäne, Anwendungsschicht, Events, REST (MockMvc), Architekturregeln: laufen überall.
- Persistenz-Tests laufen gegen H2. `PostgresPersistenceTest` prüft Flyway und Mapping gegen echtes PostgreSQL und braucht Docker, sonst wird er übersprungen.
- `OpenApiSnapshotTest` vergleicht die laufende API mit `docs/openapi.json` und schlägt bei Abweichung fehl. Die CORS-Regeln prüfen `CorsTest` und `LocalProfileTest`.
- `KickertoolEndToEndTest` spielt ein komplettes Turnier über HTTP durch (10 Spieler, 2 Tische, Dummy-Match, Ergebnisse mit Bestätigung, Admin-Korrektur, Pause, SSE) und ersetzt den OIDC-Provider durch Test-Tokens.


## Noch offen

- **Finale:** Nach X Runden soll noch eine Art Finale laufen. Das ist noch nicht gebaut. Bis dahin beendet `POST /finish` das ganze Turnier. Sobald das Finale feststeht, wird daraus das Ende der Tabellenrunde, mit der bleibenden Regel, dass es nur geht, wenn alle Ergebnisse bestätigt sind.

## Entscheidungen

Vom Auftraggeber bestätigt:

- Die nächste Welle rückt nach, sobald für alle Matches am Tisch ein Ergebnis *eingetragen* ist, ohne auf die Bestätigung zu warten. Die Runde gilt erst mit bestätigten Ergebnissen als abgeschlossen.
- Einspringer dürfen Ergebnisse eintragen und bestätigen, weil sie am Tisch stehen. Besteht ein Team nur aus Dummys ohne Einspringer, bleibt nur der Admin.
- Admin-Korrekturen sind auch nach Turnierende möglich.
- Das eintragende Team kann sein Ergebnis nicht selbst korrigieren. Ein Tippfehler läuft über Ablehnen und Admin-Entscheidung.
- Ergebnisprüfung: kein Team über dem Tor-Limit, nicht beide auf dem Limit. Unentschieden unter dem Limit ist erlaubt (Zeitablauf).
- Eine geänderte Tischzahl gilt erst ab der nächsten Welle, laufende Matches ziehen nicht um.
- Beenden (der Tabellenrunde) geht nur, wenn alle Ergebnisse bestätigt sind.
- Einspringer nur unter aktiven Teilnehmern. Spieler-Identität über `sub`, Admin meldet nur bekannte Spieler an. Ausgeschiedene melden sich nicht neu an, Abmelden nur vor dem Start.
- Teamzuteilung: die ersten X Runden komplett zufällig (X konfigurierbar), danach Rangblöcke zu je 4 mit Mischen im Block. Die Partnerregel ist ein sehr starker Wunsch und wird nur verletzt, wenn nichts anderes geht.
- Standardwert andomRounds = 2. Die Partnerregel gilt auch in den Zufallsrunden, aber nur für die letzte Runde und nur für echte Spieler (ein Einspringer als Partner zählt nicht).
- Dummys rotieren: der unvollständige Block besteht aus den Spielern, die bisher am seltensten gegen oder mit Dummys gespielt haben. Der beste Block bekommt in der Auslosung zuerst einen Tisch, der Block mit den Dummys zuletzt.

### Annahmen, die noch bestätigt werden müssen

Zurzeit keine offenen Annahmen.