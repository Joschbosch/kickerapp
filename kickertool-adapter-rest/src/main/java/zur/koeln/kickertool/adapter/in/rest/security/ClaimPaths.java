package zur.koeln.kickertool.adapter.in.rest.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Liest Werte aus verschachtelten Token-Claims. */
final class ClaimPaths {

    private ClaimPaths() {
    }

    /**
     * Löst einen Claim-Pfad auf. Zuerst wird der ganze Pfad als Claim-Name versucht (für Claims, deren Name
     * selbst Punkte enthält, z. B. URLs), dann verschachtelt entlang der Punkte.
     *
     * @return die Werte als Liste. Eine Zeichenkette wird an Leerzeichen getrennt (wie der {@code scope}-Claim).
     */
    static List<String> values(Map<String, Object> claims, String path) {
        Object value = claims.get(path);
        if (value == null) {
            value = nested(claims, path);
        }
        return toList(value);
    }

    private static Object nested(Map<String, Object> claims, String path) {
        Object current = claims;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private static List<String> toList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (value instanceof String string && !string.isBlank()) {
            return List.of(string.trim().split("\\s+"));
        }
        return List.of();
    }
}
