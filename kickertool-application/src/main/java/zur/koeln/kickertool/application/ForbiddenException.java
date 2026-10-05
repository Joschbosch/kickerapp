package zur.koeln.kickertool.application;

/** Der Actor hat nicht die nötigen Rechte. Wird zu 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
