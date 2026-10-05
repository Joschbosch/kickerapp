{{/* Name des Charts, ggf. überschrieben */}}
{{- define "kickertool.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Voller Name: Release und Chart, ohne Doppelung */}}
{{- define "kickertool.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "kickertool.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Gemeinsame Labels. Aufruf mit dict "ctx" . "component" "app" */}}
{{- define "kickertool.labels" -}}
helm.sh/chart: {{ include "kickertool.chart" .ctx }}
{{ include "kickertool.selectorLabels" . }}
app.kubernetes.io/version: {{ .ctx.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .ctx.Release.Service }}
{{- end }}

{{- define "kickertool.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kickertool.name" .ctx }}
app.kubernetes.io/instance: {{ .ctx.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{- define "kickertool.image" -}}
{{- printf "%s:%s" .Values.image.repository (default .Chart.AppVersion .Values.image.tag) }}
{{- end }}

{{/* Namen der abhängigen Dienste */}}
{{- define "kickertool.postgresqlName" -}}
{{- printf "%s-postgresql" (include "kickertool.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "kickertool.keycloakName" -}}
{{- printf "%s-keycloak" (include "kickertool.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Secret mit dem Datenbank-Passwort (Schlüssel "password") */}}
{{- define "kickertool.dbSecretName" -}}
{{- if .Values.postgresql.enabled }}
{{- default (include "kickertool.postgresqlName" .) .Values.postgresql.existingSecret }}
{{- else }}
{{- default (printf "%s-database" (include "kickertool.fullname" .)) .Values.database.existingSecret }}
{{- end }}
{{- end }}

{{- define "kickertool.dbUrl" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "jdbc:postgresql://%s:5432/%s" (include "kickertool.postgresqlName" .) .Values.postgresql.database }}
{{- else }}
{{- required "database.url ist nötig, wenn postgresql.enabled=false" .Values.database.url }}
{{- end }}
{{- end }}

{{- define "kickertool.dbUser" -}}
{{- if .Values.postgresql.enabled }}{{ .Values.postgresql.username }}{{ else }}{{ required "database.username ist nötig, wenn postgresql.enabled=false" .Values.database.username }}{{ end }}
{{- end }}

{{/* Aussteller des Tokens, so wie ihn der Browser sieht */}}
{{- define "kickertool.issuerUri" -}}
{{- if .Values.keycloak.enabled }}
{{- printf "%s/realms/%s" (trimSuffix "/" .Values.keycloak.hostname) .Values.keycloak.realm }}
{{- else }}
{{- required "oidc.issuerUri ist nötig, wenn keycloak.enabled=false" .Values.oidc.issuerUri }}
{{- end }}
{{- end }}

{{/* Schlüssel des Providers über den Cluster-Dienst (leer = Discovery über den Aussteller) */}}
{{- define "kickertool.jwkSetUri" -}}
{{- if .Values.keycloak.enabled }}
{{- printf "http://%s:8080/realms/%s/protocol/openid-connect/certs" (include "kickertool.keycloakName" .) .Values.keycloak.realm }}
{{- else }}
{{- .Values.oidc.jwkSetUri }}
{{- end }}
{{- end }}

{{/*
Ein zufälliges Passwort, das bei Upgrades gleich bleibt: gibt es das Secret schon, wird sein Wert übernommen.
Aufruf mit dict "ctx" . "name" <Secret-Name> "key" <Schlüssel> "value" <vorgegebener Wert>. Ergibt base64.
*/}}
{{- define "kickertool.secretValue" -}}
{{- if .value }}
{{- .value | b64enc }}
{{- else }}
{{- $existing := lookup "v1" "Secret" .ctx.Release.Namespace .name }}
{{- if and $existing (hasKey $existing.data .key) }}
{{- index $existing.data .key }}
{{- else }}
{{- randAlphaNum 24 | b64enc }}
{{- end }}
{{- end }}
{{- end }}
