package zur.koeln.kickertool.domain;

/** Eine Fachregel wurde verletzt (z. B. falscher Turnierstatus). Wird von der REST-Schicht zu 409. */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
