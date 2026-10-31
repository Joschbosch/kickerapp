package zur.koeln.kickertool.adapter.in.rest.openapi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;

import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Macht den API-Vertrag für typisierte Clients (z. B. aus {@code openapi-typescript}) genau:
 *
 * <ul>
 *   <li><b>Nullbare Verweise:</b> swagger-core schreibt {@code {"$ref": ..., "type": "null"}}, das hieße "muss null sein".
 *       Richtig ist {@code oneOf: [{"$ref": ...}, {"type": "null"}]}.</li>
 *   <li><b>Pflichtfelder der Antworten:</b> Das Backend sendet immer alle Felder einer Antwort, nullbare als {@code null}.
 *       Alle Eigenschaften reiner Antwort-Schemas gelten daher als Pflicht, sonst wäre in jedem Client jedes Feld
 *       optional. Bei Anfragen gilt, was mit {@code @NotNull} markiert ist.</li>
 * </ul>
 *
 * Ein Schema zählt als Antwort, wenn es von Antworten aus erreichbar ist und von keiner Anfrage aus.
 */
public class OpenApiContractCustomizer implements OpenApiCustomizer {

    private static final String SCHEMA_PREFIX = "#/components/schemas/";
    private static final String NULL_TYPE = "null";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();

        schemas.values().forEach(schema -> {
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                properties.replaceAll((name, property) ->
                        isNullableReference(property) ? nullableReference(property) : property);
            }
        });

        Set<String> usedByRequests = new HashSet<>();
        Set<String> usedByResponses = new HashSet<>();
        if (openApi.getPaths() != null) {
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(operation -> {
                if (operation.getRequestBody() != null) {
                    collect(operation.getRequestBody().getContent(), schemas, usedByRequests);
                }
                if (operation.getResponses() != null) {
                    operation.getResponses().values().forEach(response -> collect(response.getContent(), schemas,
                            usedByResponses));
                }
            }));
        }

        schemas.forEach((name, schema) -> {
            boolean responseOnly = usedByResponses.contains(name) && !usedByRequests.contains(name);
            boolean hasNoRequiredList = schema.getRequired() == null || schema.getRequired().isEmpty();
            if (responseOnly && hasNoRequiredList && schema.getProperties() != null) {
                schema.setRequired(new ArrayList<String>(schema.getProperties().keySet()));
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private static boolean isNullableReference(Schema property) {
        if (property.get$ref() == null) {
            return false;
        }
        Set<String> types = property.getTypes();
        return (types != null && types.contains(NULL_TYPE)) || NULL_TYPE.equals(property.getType());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullableReference(Schema property) {
        Schema reference = new Schema<>();
        reference.set$ref(property.get$ref());
        Schema nothing = new Schema<>();
        nothing.setTypes(Set.of(NULL_TYPE));

        Schema wrapper = new Schema<>();
        wrapper.setDescription(property.getDescription());
        wrapper.setOneOf(List.of(reference, nothing));
        return wrapper;
    }

    // ---------------------------------------------------------------- Erreichbarkeit

    @SuppressWarnings("rawtypes")
    private static void collect(Content content, Map<String, Schema> schemas, Set<String> found) {
        if (content == null) {
            return;
        }
        content.values().forEach(mediaType -> visit(mediaType.getSchema(), schemas, found));
    }

    /** Sammelt alle Schemas, die von {@code schema} aus über Verweise erreichbar sind. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void visit(Schema schema, Map<String, Schema> schemas, Set<String> found) {
        if (schema == null) {
            return;
        }
        String reference = schema.get$ref();
        if (reference != null && reference.startsWith(SCHEMA_PREFIX)) {
            String name = reference.substring(SCHEMA_PREFIX.length());
            if (found.add(name)) {
                visit(schemas.get(name), schemas, found);
            }
        }
        if (schema.getProperties() != null) {
            ((Map<String, Schema>) schema.getProperties()).values().forEach(p -> visit(p, schemas, found));
        }
        visit(schema.getItems(), schemas, found);
        for (List<Schema> composed : java.util.Arrays.asList(schema.getOneOf(), schema.getAllOf(), schema.getAnyOf())) {
            if (composed != null) {
                composed.forEach(s -> visit(s, schemas, found));
            }
        }
    }
}
