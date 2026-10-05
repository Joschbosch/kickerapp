package zur.koeln.kickertool.application.port.out;

/** Ein laufendes Abo auf Turnier-Events. */
public interface EventSubscription extends AutoCloseable {

    @Override
    void close();
}
