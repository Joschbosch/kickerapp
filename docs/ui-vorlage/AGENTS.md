# Kickertool UI

> Vorlage: in das Wurzelverzeichnis des UI-Repos kopieren, als `AGENTS.md` (gilt für Codex und andere) und bei Bedarf
> `CLAUDE.md` mit dem Inhalt `@AGENTS.md`. Die Stellen in `<spitzen Klammern>` anpassen, sobald sie feststehen.

Oberfläche für das Kickertool, eine Kicker-Turnierverwaltung (2 gegen 2, wechselnde Partner, mehrere Tische in Wellen,
Einzelrangliste). Die UI ist ein reiner Client der Backend-REST-API und enthält keine Turnierlogik.

- Backend-Repo: https://github.com/Joschbosch/kickerapp (Branch `main`)
- Übergabe mit allen Details: `docs/ui-uebergabe.md` im Backend-Repo. **Zuerst lesen.**
- API-Vertrag: `docs/openapi.json` im Backend-Repo (Swagger UI am laufenden Backend unter `/swagger-ui.html`).

## Technik
- Stack: `<noch festzulegen, z. B. React + Vite + TypeScript oder Angular>`
- Auslieferung: `<noch festzulegen: Web-App/PWA oder native App; eigene Adresse oder unter dem Backend>`
- Paketverwaltung, Start, Tests: `<Befehle eintragen, z. B. npm install, npm run dev, npm test>`
- Sprache der Oberfläche und der Dokumentation: Deutsch. Code-Bezeichner: Englisch.

## Arbeitsweise
- Bei Unklarheiten in den Anforderungen **nachfragen**, nicht selbst entscheiden. Getroffene Annahmen offen benennen.
- Die UI arbeitet nur über die API. Fachregeln (wer darf bestätigen, Punkte, Auslosung, Rangliste) werden **nicht** in
  der UI nachgebaut: Die Flags `permissions` und `mySide` an den Matches und die Rangliste kommen vom Backend.
- Fehlt etwas in der API, ist das eine Anfrage an das Backend-Repo, kein Grund für eine Umgehung in der UI.
- Fehler der API (`application/problem+json`) werden mit dem Feld `detail` angezeigt, es ist eine deutsche Meldung.

## API-Client
- Der Client wird aus `docs/openapi.json` **erzeugt** und nicht von Hand gepflegt. Befehl und Ablageort:
  `<z. B. npx openapi-typescript <Pfad>/openapi.json -o src/api/schema.d.ts>`
- Ändert sich der Vertrag im Backend, die Datei übernehmen und den Client neu erzeugen. Erzeugte Dateien nicht von Hand
  ändern.
- Typen (Turnier, Match, Rangliste, Statuswerte) kommen aus dem erzeugten Client, nicht aus eigenen Kopien.

## Backend lokal starten
- JDK 21 oder neuer, dann im Backend-Repo `./scripts/run-local.sh` (Windows `.\scripts\run-local.ps1`). Das startet die
  API auf http://localhost:8080 mit einem simulierten Login und einem Demo-Turnier. Details in der Übergabe, Abschnitt 2.
- Testnutzer (Benutzername = Passwort): `admin` (Admin), `anna`, `ben`, `clara`, `david`, `emma`, `felix`, `greta`, `hans`,
  `ida`, `jonas`. Token: `POST /mock-oidc/token` mit `grant_type=password`.
- Läuft die UI auf einem anderen lokalen Port (3000, 4200, 5173, ...), ist CORS im lokalen Modus schon freigegeben.
- Ohne laufendes Backend: Mock-Server aus `docs/openapi.json` (siehe Übergabe, Abschnitt 2).

## Anmeldung
- OIDC mit Authorization Code und PKCE, Client `kickertool-app` (öffentlich, kein Secret), Aussteller steht im Vertrag.
  Jeder Aufruf schickt `Authorization: Bearer <access_token>`.
- Der Admin-Status kommt aus `GET /api/me` (`admin`), durchgesetzt wird er im Backend.
- Tokens und Passwörter nie ins Repo, in Logs oder in URLs schreiben.

## Live-Updates
- Der Browser-`EventSource` kann keinen Authorization-Header senden. Ablauf: `POST /api/tournaments/{id}/events/ticket` mit dem
  Token, dann `new EventSource(BACKEND + streamUrl)` (Ticket in der Adresse). Details und Skizze in der Übergabe, Abschnitt 6.
- Die Events enthalten keine Daten, die UI lädt nach einem Event neu. Jedes Event hat eine `id`: merken und beim erneuten Verbinden
  mitschicken (`Last-Event-ID` macht der Browser von allein, bei einem neuen `EventSource` als `&lastEventId=` anhängen). Ein
  `RESYNC` heißt: alles neu laden.
- Tabellen-Bildschirme laufen stundenlang: Token erneuern, bei Abbruch (`readyState === CLOSED`) mit neuem Ticket neu verbinden.

## Prüfen vor dem Abschluss
- `<Lint, Typprüfung und Tests ausführen: Befehle eintragen>`
- Gegen das lokale Backend durchspielen: Als `admin` das Demo-Turnier starten und eine Runde auslosen, als Spieler ein
  Ergebnis eintragen, als Gegner bestätigen, Rangliste prüfen.
