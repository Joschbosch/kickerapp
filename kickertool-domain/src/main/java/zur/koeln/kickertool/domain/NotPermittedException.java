package zur.koeln.kickertool.domain;

/** Der Handelnde darf diese Aktion fachlich nicht ausführen (z. B. nicht Teil des Teams). Wird zu 403. */
public class NotPermittedException extends RuntimeException {

    public NotPermittedException(String message) {
        super(message);
    }
}
