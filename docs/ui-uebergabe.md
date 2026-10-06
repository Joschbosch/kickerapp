# Übergabe für die UI-Entwicklung

Dieses Dokument ist für alle, die eine Oberfläche für das Kickertool bauen (mit oder ohne KI-Assistent wie Codex oder
Claude). Das Backend ist eine reine REST-API. Die UI liegt in einem eigenen Repo und spricht nur über diese API mit dem
Backend. Alles Fachliche steht ausführlich im [README](../README.md), hier steht, was man für die UI braucht.

## 1. Worum es geht

Ein Kicker-Turnier mit vielen Spielern (z. B. 50), bei dem in jeder Runde 2 gegen 2 gespielt wird und die Partner
wechseln. Es gibt nur wenige Tische, deshalb spielen die Matches einer Runde in **Wellen**. Spieler tragen ihre
Ergebnisse am Handy selbst ein, das Gegnerteam bestätigt. Der **Admin** (Turnierleitung) plant das Turnier, startet die
Runden und entscheidet Streitfälle. Daraus entsteht eine Einzelrangliste.

Wer was braucht:

| Rolle | Braucht |
|---|---|
| Spieler (Handy) | anmelden, "wo muss ich spielen" (Tisch, Partner, Gegner), Ergebnis eintragen oder bestätigen, pausieren oder ausscheiden, Rangliste |
| Admin | Turnier planen und konfigurieren, starten, Runden auslosen, Ergebnisse festlegen und korrigieren, Tischzahl ändern, Turnier beenden |
| Anzeige (Großbildschirm, optional) | Rangliste und aktuelle Runde mit Tischen, nur lesen |

## 2. Backend lokal starten

Voraussetzung: **JDK 21 oder neuer** und Internet (Maven lädt beim ersten Start Abhängigkeiten). Docker ist nicht nötig.

```bash
git clone https://github.com/Joschbosch/kickerapp.git
cd kickerapp
./scripts/run-local.sh          # Windows: .\scripts\run-local.ps1
```

Das Skript prüft Java und Port, baut alles und startet die App auf http://localhost:8080 (anderer Port:
`PORT=9090 ./scripts/run-local.sh`, Windows: `-Port 9090`). Der erste Start dauert einige Minuten.

Im lokalen Modus gibt es:

- **H2 im Speicher** statt Datenbank. Nach jedem Neustart ist alles wieder frisch.
- **Simulierter Login** (`/mock-oidc`), kein Keycloak nötig. Benutzername = Passwort: `admin` (Admin-Rechte), `anna`,
  `ben`, `clara`, `david`, `emma`, `felix`, `greta`, `hans`, `ida`, `jonas`.
- **Ein Demo-Turnier** "Demo-Turnier" mit allen 10 Spielern angemeldet, noch nicht gestartet, 3 Tische.
- **Swagger UI** unter http://localhost:8080/swagger-ui.html zum Ausprobieren. Dort **Authorize**, `oidc`, Benutzer
  eintragen.
- **CORS** für jede lokale Adresse (`http://localhost:*`), die UI darf also auf beliebigem Dev-Port laufen.

Mit echtem Keycloak und Postgres (Docker): `docker compose --profile app up -d --build`, siehe README. Das braucht
Docker und läuft dann ebenfalls auf Port 8080, Keycloak auf 8180.

### Ohne Backend arbeiten (nur der Vertrag)

[`docs/openapi.json`](openapi.json) beschreibt die komplette API und liegt eingecheckt im Repo. Daraus kann man ohne
laufendes Backend einen typisierten Client erzeugen oder einen Mock-Server starten. Beispiele (nicht mit diesem Projekt
ausprobiert, bitte prüfen):

```bash
npx openapi-typescript docs/openapi.json -o src/api/schema.d.ts      # TypeScript-Typen
npx @stoplight/prism-cli mock docs/openapi.json                       # Mock-Server mit Beispielantworten
```

Der Vertrag ist aktuell, solange die Tests im Backend laufen: `OpenApiSnapshotTest` schlägt fehl, wenn sich die API
ändert, ohne dass die Datei nachgezogen wurde.

## 3. Anmeldung

Registrierung und Login übernimmt ein OIDC-Provider (Keycloak). Die API prüft nur das Access-Token.

- **UI-Ablauf:** Authorization Code mit **PKCE (S256)**, Client `kickertool-app` (öffentlicher Client, kein Secret).
  Den Provider über die Discovery finden: `{Aussteller}/.well-known/openid-configuration`. Der Aussteller steht in der
  OpenAPI-Datei unter `components.securitySchemes.oidc.openIdConnectUrl`. Jeder Request schickt
  `Authorization: Bearer <access_token>`.
- **Spieler anlegen:** Passiert automatisch beim ersten Aufruf mit gültigem Token. `GET /api/me` liefert `id`,
  `displayName` und `admin` (ob der Aufrufer Admin ist).
- **Admin:** Wer in Keycloak die Realm-Rolle `kicker-admin` hat. Die UI blendet Admin-Funktionen anhand von `admin` aus
  `/api/me` ein, durchgesetzt wird es im Backend (sonst 403).
- **Lokaler Modus:** Der simulierte Provider kennt **nur den Password-Flow** (Benutzername, Passwort, an den
  Token-Endpunkt), keinen Browser-Login. Für die Entwicklung reicht das:

  ```bash
  curl -d "grant_type=password&client_id=kickertool-app&username=anna&password=anna" http://localhost:8080/mock-oidc/token
  ```

  Das Token gilt 8 Stunden. Für den echten Browser-Login (Weiterleitung, PKCE) braucht man den Docker-Stack mit
  Keycloak. Dort sind alle Rückleitungen erlaubt (`redirectUris: *`, nur für lokale Tests).
- **Rückleitungen im Cluster:** Beim Helm-Chart mit `values-local.yaml` sind die Ports 3000, 4200 und 5173 freigegeben.
  Andere UI-Adressen müssen in `keycloak.redirectUris` und `keycloak.webOrigins` eingetragen werden.

## 4. Der API-Vertrag in Kürze

Alle Endpunkte liegen unter `/api` und brauchen ein Token. Vollständig beschrieben in Swagger UI und `openapi.json`.
Hier die Zuordnung zu Bildschirmen:

| Bildschirm | Endpunkte |
|---|---|
| Start, Profil | `GET /api/me` |
| Turnierliste | `GET /api/tournaments` |
| Turnier, Anmeldung | `GET /api/tournaments/{id}`, `POST …/participants`, `DELETE …/participants/me`, `PUT …/participants/me/status` |
| Mein Spiel | `GET …/matches/mine` (alle Matches des Aufrufers, auch als Einspringer) |
| Ergebnis | `POST …/matches/{id}/result-proposal`, dann `…/result-proposal/confirmation` oder `…/rejection` |
| Rangliste | `GET …/ranking` |
| Runden und Tische | `GET …/rounds/current`, `GET …/rounds` |
| Admin | `POST /api/tournaments`, `PUT …/config`, `POST …/start`, `POST …/rounds`, `PUT …/matches/{id}/result`, `POST …/finish` |
| Live-Updates | `GET …/events` (siehe Abschnitt 6) |

Konventionen:

- IDs sind UUIDs. Als Spieler-ID im Pfad geht auch `me`. Datum `2026-10-05`, Zeitpunkte ISO 8601 in UTC.
- Fehler kommen als `application/problem+json` (`status`, `detail`): 400 ungültige Eingabe (z. B. Ergebnis über dem
  Tor-Limit), 401 kein oder ungültiges Token, 403 keine Berechtigung, 404 nicht gefunden, 409 passt nicht zum Stand
  (z. B. "Runde nicht abgeschlossen"). `detail` ist eine deutsche, anzeigbare Meldung.
- Es gibt keine Seitenaufteilung (Paginierung), die Datenmengen sind klein (ein Turnier mit 50 Spielern).
- Mutierende Aufrufe liefern den neuen Stand zurück (Turnier oder Match), die UI muss danach nicht nachladen.

### Was der Aufrufer darf: `permissions`

Jedes Match enthält für den **Aufrufer** berechnete Flags, damit die UI die Regeln nicht nachbauen muss:

```json
{ "mySide": "A",
  "permissions": { "canEnterResult": true, "canConfirm": false, "canReject": false, "canDecide": false } }
```

- `canEnterResult`: das Match läuft am Tisch und der Aufrufer steht dort. Button "Ergebnis eintragen".
- `canConfirm` und `canReject`: ein Ergebnis steht, und der Aufrufer gehört zum **Gegnerteam** des Eintragenden.
- `canDecide`: der Aufrufer ist Admin und das Match läuft oder ist gespielt (auch bestätigte lassen sich korrigieren).
- `mySide` (`A` oder `B`, sonst `null`): auf welcher Seite der Aufrufer steht, z. B. um "mein Team" hervorzuheben.

Die Antworten sind pro Aufrufer verschieden und dürfen nicht zwischen Nutzern geteilt oder gemeinsam gecacht werden.
Für alles andere (Anmelden, Pausieren) gibt es keine Flags: Die Teilnehmerliste im Turnier und der Turnierstatus
reichen (angemeldet = eigene `id` aus `/api/me` steht in `participants`).

## 5. Fachliche Zustände, die die UI anzeigen muss

**Turnier** (`status`): `PLANNED` (Anmeldung offen, Konfiguration), `RUNNING` (Runden laufen), `FINISHED`.
Anmelden ist bis zum Ende möglich, wer nach dem Start kommt, spielt ab der nächsten Runde mit. Abmelden geht nur in
`PLANNED`.

**Teilnehmer** (`status`): `ACTIVE`, `PAUSED` (ab der nächsten Runde nicht mehr zugelost, kann zurückkehren) und
`WITHDRAWN` (ausgeschieden, endgültig, bisherige Punkte bleiben und sind in der Rangliste mit diesem Status markiert).

**Match** (`status`):

```
QUEUED  ->  ON_TABLE  ->  RESULT_ENTERED  ->  CONFIRMED
(wartet)    (spielt)      (wartet auf         (zählt für die
                           Bestätigung)        Rangliste)
                          \-> DISPUTED (Gegner lehnte ab, Admin entscheidet) -> CONFIRMED
```

- **Wellen und Tische:** Pro Runde bekommen die ersten Matches einen Tisch (`table`, ab 1), die übrigen warten
  (`QUEUED`, `table` ist `null`). Sobald für alle Matches am Tisch ein Ergebnis **eingetragen** ist, rückt die nächste
  Welle nach. Die UI zeigt jedem Spieler also entweder "Tisch 3, jetzt spielen" oder "du bist als Nächstes dran".
- **Dummys:** Fehlen Spieler zur vollen Vierergruppe, steht im Team ein Platz mit `type: "DUMMY"` statt eines Spielers.
  Dort spielt ein `standIn` (ein anderer Turnierteilnehmer, der gerade nicht am Tisch steht) oder, wenn `standIn` `null`
  ist, jemand aus dem Publikum. Dummys und Einspringer bekommen für dieses Match keine Punkte, Einspringer dürfen aber
  Ergebnisse eintragen und bestätigen. Die UI sollte das deutlich anzeigen ("Gast" o. ä.).
- **Ergebnis:** Ein Spiel geht bis zum Tor-Limit (`config.goalLimit`, Standard 10) oder zur Zeit (`matchMinutes`,
  Standard 5), nur ein Satz. Das Backend prüft: kein Team über dem Limit, nicht beide auf dem Limit. Unentschieden unter
  dem Limit ist erlaubt (Zeitablauf). `result.confirmed` ist `false`, solange das Ergebnis nicht zählt.
- **Runde abgeschlossen** (`complete`), wenn alle Ergebnisse bestätigt sind. Erst dann kann der Admin die nächste
  Runde auslosen (sonst 409). Pausieren und Ausscheiden wirken erst ab der nächsten Runde.
- **Rangliste:** Punkte, dann Tordifferenz, dann erzielte Tore. Gleiche Werte teilen sich den Rang. Es zählen nur
  bestätigte Ergebnisse. Felder: `rank`, `points`, `matchesPlayed`, `wins`, `draws`, `losses`, `goalsFor`,
  `goalsAgainst`, `goalDifference`, `status`.
- **Konfiguration** (`config`): `tableCount` (auch während des Turniers änderbar, gilt ab der nächsten Welle),
  Punkte für Sieg, Unentschieden, Niederlage, `goalLimit`, `matchMinutes`, `plannedRounds` (nur Anzeige, `null` = offen)
  und `randomRounds` (so viele erste Runden werden zufällig ausgelost, danach nach Rangliste).

## 6. Live-Updates

`GET /api/tournaments/{id}/events` liefert **Server-Sent Events**. Die Events enthalten **keine Daten**, nur den Hinweis,
dass sich etwas geändert hat. Die UI lädt danach den neuen Stand über die normalen Endpunkte.

| Event | Wann |
|---|---|
| `connected` | direkt nach dem Verbinden |
| `TOURNAMENT_CHANGED` | Name, Datum, Konfiguration oder Status geändert |
| `PARTICIPANTS_CHANGED` | Anmeldung, Pause, Ausscheiden |
| `ROUND_STARTED` | neue Runde ausgelost |
| `MATCHES_CHANGED` | Tische, Status oder Ergebnisse (mit `matchId`, wenn ein einzelnes Match betroffen ist) |
| `RANKING_CHANGED` | Rangliste hat sich geändert |

Daten eines Events: `{ "type": "MATCHES_CHANGED", "matchId": "…", "occurredAt": "2026-10-05T20:15:00Z" }`.

Wichtig:

- **Der Stream braucht den `Authorization`-Header.** Der Browser-`EventSource` kann keine eigenen Header senden. Man
  braucht eine Fetch-basierte Lösung (z. B. die Bibliothek `@microsoft/fetch-event-source`) oder eine native
  HTTP-Verbindung in der App. Alternativ pollt die UI (z. B. alle 5 Sekunden), das reicht für ein Turnier.
- Alle 20 Sekunden kommt ein Kommentar (`: ping`), nach spätestens 30 Minuten endet die Verbindung. Die UI **verbindet
  sich neu** und lädt danach den Stand frisch, verpasste Events werden nicht nachgeliefert.
- Die Verteilung arbeitet im Speicher einer Backend-Instanz. Das passt zum Betrieb mit einer Instanz.

## 7. Bekannte Lücken und Eigenheiten

- **Finale nach X Runden** ist noch nicht gebaut. Bis dahin beendet `POST …/finish` das ganze Turnier.
- **Push-Benachrichtigungen** aufs Handy ("dein Match startet") gibt es nicht, nur den Event-Stream bei geöffneter App.
- **Teamzuteilung** (Zufall in den ersten Runden, danach Rangblöcke zu je 4, keine gleichen Partner in zwei aufeinander-
  folgenden Runden) ist ein erster Ansatz und kann sich noch ändern. Die API bleibt davon unberührt.
- **Tippfehler im Ergebnis:** Das eintragende Team kann nicht selbst korrigieren. Das Gegnerteam lehnt ab, der Admin
  entscheidet (`canDecide`).
- **`POST …/participants` ohne Body:** `fetch` und curl sind unproblematisch. Werkzeuge, die bei leerem Body einen
  Formular-Content-Type setzen (z. B. PowerShell), bekommen 415. Dann `Content-Type: application/json` und `{}` senden.
- **Spieler anlegen/umbenennen** gibt es nicht: Name und Identität kommen aus dem OIDC-Token.
- **Mehrsprachigkeit:** Fehlermeldungen (`detail`) sind Deutsch.

## 8. Offene Entscheidungen (vor dem Start klären)

- **Native App oder Web-App (PWA)?** Der Auftraggeber spricht von einer "App auf dem Handy". Das beeinflusst Login
  (Weiterleitung), Live-Updates und Push.
- **Wo wird die UI ausgeliefert?** Unter derselben Adresse wie das Backend (Ingress mit Pfaden) entfällt CORS. Auf einer
  eigenen Adresse muss sie im Backend freigegeben werden (`KICKERTOOL_CORS_ALLOWED_ORIGINS`) und im Keycloak als
  Rückleitung eingetragen sein.
- **Großbildschirm-Ansicht** (Rangliste und Tische ohne Login)? Das ginge im Moment nicht, alle Endpunkte brauchen ein
  Token. Dafür bräuchte es eine bewusste Entscheidung für öffentlich lesbare Endpunkte.

## 9. Arbeiten mit KI-Assistenten

- Im Backend-Repo steht `AGENTS.md` (gilt für Codex und andere) und `CLAUDE.md` (verweist darauf). Für das UI-Repo gibt
  es eine Vorlage: [`docs/ui-vorlage/AGENTS.md`](ui-vorlage/AGENTS.md), einfach ins UI-Repo kopieren und anpassen.
- Codex läuft oft in einer Sandbox ohne Netz oder Docker. Das Backend lokal zu starten braucht Netz (Maven-Abhängigkeiten).
  Wer das nicht will, arbeitet gegen `docs/openapi.json` und einen Mock-Server (Abschnitt 2) oder lässt das Backend
  außerhalb der Sandbox laufen.
- Der Assistent sollte nur über die API arbeiten, nie am Backend vorbei. Fehlt etwas in der API, ist das eine Anfrage an
  das Backend-Repo (siehe unten), kein Grund, Logik in der UI zu duplizieren.

## 10. Wenn sich die API ändern muss

Gewünschte Änderungen (z. B. ein weiteres Flag, ein neuer Endpunkt) im Backend-Repo anfragen. Wer sie umsetzt, erzeugt
danach `docs/openapi.json` neu (`./mvnw -pl kickertool-bootstrap test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true`),
und die UI zieht ihren Client nach.
