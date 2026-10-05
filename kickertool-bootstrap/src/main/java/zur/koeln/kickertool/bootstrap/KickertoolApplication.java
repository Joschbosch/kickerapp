package zur.koeln.kickertool.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Startet die Anwendung. Die Adapter-Module bringen ihre Konfiguration selbst mit und werden über das Scannen
 * von {@code zur.koeln.kickertool} gefunden, die Anwendungsdienste verdrahtet {@link UseCaseConfiguration}.
 */
@SpringBootApplication(scanBasePackages = "zur.koeln.kickertool")
public class KickertoolApplication {

    public static void main(String[] args) {
        SpringApplication.run(KickertoolApplication.class, args);
    }
}
