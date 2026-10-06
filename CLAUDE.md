@AGENTS.md

# Zusätze für Claude Code auf dieser Maschine

Die allgemeinen Projektregeln stehen in `AGENTS.md` (gilt für alle Assistenten). Hier nur, was für Claude Code oder
diesen Rechner gilt.

- Auf dieser Maschine ist kein JDK im PATH: vor Maven `$env:JAVA_HOME="$env:USERPROFILE\.jdks\openjdk-26.0.1"` setzen
  (JDK 26, kompiliert wird trotzdem mit `--release 21`). `scripts/run-local.ps1` findet es selbst.
- Docker (Rancher Desktop mit k3s, Helm 4, kubectl) ist installiert, läuft aber nicht immer.
- Der Auftraggeber will bei Unklarheiten gefragt werden und Annahmen offen genannt bekommen (siehe `AGENTS.md`).
