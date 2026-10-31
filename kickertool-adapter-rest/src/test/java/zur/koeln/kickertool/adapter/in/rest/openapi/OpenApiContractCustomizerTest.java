package zur.koeln.kickertool.adapter.in.rest.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.Test;

class OpenApiContractCustomizerTest {

    private static Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private static Content json(String schemaName) {
        return new Content().addMediaType("application/json", new MediaType().schema(ref(schemaName)));
    }

    /**
     * Eine Antwort "Match" (mit nullbarem Verweis, wie swagger-core ihn falsch schreibt), die verschachtelte Antwort
     * "Result" und eine Anfrage "ResultRequest" mit eigener Pflichtliste.
     */
    private OpenAPI contract() {
        Schema<?> nullableReference = ref("Result").types(Set.of("null"));
        nullableReference.setDescription("null solange kein Ergebnis vorliegt");

        Components components = new Components()
                .addSchemas("Match", new ObjectSchema()
                        .addProperty("id", new StringSchema())
                        .addProperty("table", new IntegerSchema().types(Set.of("integer", "null")))
                        .addProperty("result", nullableReference))
                .addSchemas("Result", new ObjectSchema().addProperty("goalsA", new IntegerSchema()))
                .addSchemas("ResultRequest", new ObjectSchema()
                        .addProperty("goalsA", new IntegerSchema())
                        .addProperty("comment", new StringSchema())
                        .required(List.of("goalsA")))
                .addSchemas("Unused", new ObjectSchema().addProperty("x", new StringSchema()));

        Paths paths = new Paths()
                .addPathItem("/matches/{id}", new PathItem().get(new Operation()
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(json("Match"))))))
                .addPathItem("/matches/{id}/result", new PathItem().post(new Operation()
                        .requestBody(new RequestBody().content(json("ResultRequest")))
                        .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(json("Match"))))));
        return new OpenAPI().components(components).paths(paths);
    }

    @Test
    void writesNullableReferencesAsOneOfWithNull() {
        OpenAPI api = contract();

        new OpenApiContractCustomizer().customise(api);

        Schema<?> result = (Schema<?>) api.getComponents().getSchemas().get("Match").getProperties().get("result");
        assertThat(result.get$ref()).as("kein $ref neben type null").isNull();
        assertThat(result.getTypes()).isNull();
        assertThat(result.getDescription()).isEqualTo("null solange kein Ergebnis vorliegt");
        assertThat(result.getOneOf()).hasSize(2);
        assertThat(result.getOneOf().get(0).get$ref()).isEqualTo("#/components/schemas/Result");
        assertThat(result.getOneOf().get(1).getTypes()).containsExactly("null");
    }

    @Test
    void leavesPlainNullableValuesAlone() {
        OpenAPI api = contract();

        new OpenApiContractCustomizer().customise(api);

        Schema<?> table = (Schema<?>) api.getComponents().getSchemas().get("Match").getProperties().get("table");
        assertThat(table.getTypes()).containsExactlyInAnyOrder("integer", "null");
    }

    @Test
    void marksAllPropertiesOfResponseSchemasAsRequired() {
        OpenAPI api = contract();

        new OpenApiContractCustomizer().customise(api);

        Map<String, Schema> schemas = api.getComponents().getSchemas();
        assertThat(schemas.get("Match").getRequired()).containsExactlyInAnyOrder("id", "table", "result");
        assertThat(schemas.get("Result").getRequired()).as("auch über Verweise erreichbare Antworten")
                .containsExactly("goalsA");
    }

    @Test
    void keepsTheRequiredListOfRequestsAsDeclared() {
        OpenAPI api = contract();

        new OpenApiContractCustomizer().customise(api);

        assertThat(api.getComponents().getSchemas().get("ResultRequest").getRequired()).containsExactly("goalsA");
    }

    @Test
    void doesNotTouchSchemasNoResponseUses() {
        OpenAPI api = contract();

        new OpenApiContractCustomizer().customise(api);

        assertThat(api.getComponents().getSchemas().get("Unused").getRequired()).isNullOrEmpty();
    }

    @Test
    void aSchemaUsedByRequestsAndResponsesKeepsItsOwnRequiredList() {
        OpenAPI api = contract();
        // "Result" wird zusätzlich als Anfrage verwendet
        api.getPaths().get("/matches/{id}/result").getPost().getRequestBody().setContent(json("Result"));

        new OpenApiContractCustomizer().customise(api);

        assertThat(api.getComponents().getSchemas().get("Result").getRequired()).isNullOrEmpty();
    }

    @Test
    void copesWithAnEmptyDocument() {
        new OpenApiContractCustomizer().customise(new OpenAPI());
    }
}
