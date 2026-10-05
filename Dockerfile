# syntax=docker/dockerfile:1

# ---- Build: Maven-Build der Anwendung (Tests laufen in der CI, nicht beim Image-Bau) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY kickertool-domain/pom.xml kickertool-domain/pom.xml
COPY kickertool-application/pom.xml kickertool-application/pom.xml
COPY kickertool-adapter-persistence/pom.xml kickertool-adapter-persistence/pom.xml
COPY kickertool-adapter-events/pom.xml kickertool-adapter-events/pom.xml
COPY kickertool-adapter-rest/pom.xml kickertool-adapter-rest/pom.xml
COPY kickertool-local/pom.xml kickertool-local/pom.xml
COPY kickertool-bootstrap/pom.xml kickertool-bootstrap/pom.xml
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY kickertool-domain/src kickertool-domain/src
COPY kickertool-application/src kickertool-application/src
COPY kickertool-adapter-persistence/src kickertool-adapter-persistence/src
COPY kickertool-adapter-events/src kickertool-adapter-events/src
COPY kickertool-adapter-rest/src kickertool-adapter-rest/src
COPY kickertool-local/src kickertool-local/src
COPY kickertool-bootstrap/src kickertool-bootstrap/src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests -pl kickertool-bootstrap -am package \
    && mkdir -p /workspace/app \
    && cp kickertool-bootstrap/target/kickertool-bootstrap-*.jar /workspace/app/application.jar

# Das Spring-Boot-Jar in Schichten zerlegen: Abhängigkeiten ändern sich selten, der eigene Code oft.
RUN java -Djarmode=tools -jar /workspace/app/application.jar extract --layers --launcher --destination /workspace/extracted

# ---- Laufzeit: schlankes JRE-Image, ohne Root ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system --gid 10001 kicker && useradd --system --uid 10001 --gid kicker --no-create-home kicker

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# Der Container richtet seinen Speicher nach dem Limit des Orchestrators. Eigene Optionen über JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
