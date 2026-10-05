package zur.koeln.kickertool.domain;

/** Ein referenziertes Objekt existiert nicht. Wird zu 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
