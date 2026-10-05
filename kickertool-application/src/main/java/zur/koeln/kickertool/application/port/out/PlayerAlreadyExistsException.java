package zur.koeln.kickertool.application.port.out;

/** Ein Spieler mit dieser {@code subject}-Kennung existiert bereits (z. B. durch parallele Erstanmeldung). */
public class PlayerAlreadyExistsException extends RuntimeException {

    public PlayerAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
