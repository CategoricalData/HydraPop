package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.core.Term;
import hydra.encode.core.Core;
import hydra.encode.pg.model.Model;
import hydra.json.encode.Encode;
import hydra.json.model.Value;
import hydra.json.writer.Writer;
import hydra.pg.model.Edge;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.pg.model.PropertyKey;
import hydra.pg.model.Vertex;
import hydra.util.Either;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates JSON files from the example graphs and schema defined in Java.
 * The output is written to src/gen-main/json/ and can be loaded by tests
 * in any language using Hydra's JSON decoders.
 *
 * <p>This is a translingual interchange approach: the Java definitions in
 * {@link ExampleGraphs} are the source of truth, encoded as Hydra Terms,
 * and serialized to a language-independent JSON representation.
 *
 * <p>Usage: {@code ./gradlew generateExampleData}
 */
public class GenerateExampleData {

    private static final String OUTPUT_BASE = "src/gen-main/json";

    public static void main(String[] args) throws Exception {
        String outputBase = args.length > 0 ? args[0] : OUTPUT_BASE;
        Path outputDir = Paths.get(outputBase);
        Files.createDirectories(outputDir);

        System.out.println("Writing PG data to " + outputDir);

        // Build the example data (Java is the source of truth)
        GraphSchema<LiteralType> schema = ExampleGraphs.buildModernGraphSchema();
        Graph<Literal> validGraph = ExampleGraphs.buildModernGraph();

        // Schema
        writeJsonFile(outputDir, "schema.json", encodeSchemaToJson(schema));

        // Valid graph
        writeJsonFile(outputDir, "modern_graph.json", encodeGraphToJson(validGraph));

        // Invalid graph variants for validation tests
        writeJsonFile(outputDir, "missing_required_property.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Vertex<Literal> marko = vertices.get(id1);
                    java.util.Map<PropertyKey, Literal> props = new java.util.HashMap<>(marko.properties);
                    props.remove(new PropertyKey("name"));
                    vertices.put(id1, new Vertex<>(marko.label, marko.id, props));
                })));

        writeJsonFile(outputDir, "wrong_id_type.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal stringId = hydra.dsl.Literals.string("notAnInt");
                    vertices.put(stringId, hydra.pg.dsl.Graphs.<Literal>vertex("person", stringId)
                            .property("name", hydra.dsl.Literals.string("zaphod"))
                            .build());
                })));

        writeJsonFile(outputDir, "unexpected_vertex_label.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal id = hydra.dsl.Literals.int32(100);
                    vertices.put(id, hydra.pg.dsl.Graphs.<Literal>vertex("robot", id)
                            .property("name", hydra.dsl.Literals.string("bender"))
                            .build());
                })));

        writeJsonFile(outputDir, "unexpected_edge_label.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal edgeId = hydra.dsl.Literals.int32(100);
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Literal id2 = hydra.dsl.Literals.int32(2);
                    edges.put(edgeId, hydra.pg.dsl.Graphs.<Literal>edge("manages", edgeId, id1, id2)
                            .build());
                })));

        writeJsonFile(outputDir, "property_value_type_mismatch.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Vertex<Literal> marko = vertices.get(id1);
                    java.util.Map<PropertyKey, Literal> props = new java.util.HashMap<>(marko.properties);
                    props.put(new PropertyKey("name"), hydra.dsl.Literals.int32(42));
                    vertices.put(id1, new Vertex<>(marko.label, marko.id, props));
                })));

        writeJsonFile(outputDir, "unexpected_property_key.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Vertex<Literal> marko = vertices.get(id1);
                    java.util.Map<PropertyKey, Literal> props = new java.util.HashMap<>(marko.properties);
                    props.put(new PropertyKey("favoriteColor"), hydra.dsl.Literals.string("blue"));
                    vertices.put(id1, new Vertex<>(marko.label, marko.id, props));
                })));

        writeJsonFile(outputDir, "wrong_in_vertex_label.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal edgeId = hydra.dsl.Literals.int32(100);
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Literal id3 = hydra.dsl.Literals.int32(3);
                    edges.put(edgeId, hydra.pg.dsl.Graphs.<Literal>edge("knows", edgeId, id1, id3)
                            .property("weight", hydra.dsl.Literals.float64(0.5))
                            .build());
                })));

        writeJsonFile(outputDir, "wrong_out_vertex_label.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal edgeId = hydra.dsl.Literals.int32(100);
                    Literal id3 = hydra.dsl.Literals.int32(3);
                    Literal id5 = hydra.dsl.Literals.int32(5);
                    edges.put(edgeId, hydra.pg.dsl.Graphs.<Literal>edge("created", edgeId, id3, id5)
                            .property("weight", hydra.dsl.Literals.float64(0.5))
                            .build());
                })));

        writeJsonFile(outputDir, "missing_required_edge_property.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal edgeId = hydra.dsl.Literals.int32(100);
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Literal id2 = hydra.dsl.Literals.int32(2);
                    edges.put(edgeId, hydra.pg.dsl.Graphs.<Literal>edge("knows", edgeId, id1, id2)
                            .build());
                })));

        writeJsonFile(outputDir, "unknown_edge_endpoint.json",
                encodeGraphToJson(buildModifiedGraph((vertices, edges) -> {
                    Literal edgeId = hydra.dsl.Literals.int32(100);
                    Literal id1 = hydra.dsl.Literals.int32(1);
                    Literal idMissing = hydra.dsl.Literals.int32(999);
                    edges.put(edgeId, hydra.pg.dsl.Graphs.<Literal>edge("knows", edgeId, id1, idMissing)
                            .property("weight", hydra.dsl.Literals.float64(0.5))
                            .build());
                })));

        System.out.println("Done. Wrote " + 12 + " JSON files.");
    }

    // -- Encoding helpers --

    private static String encodeSchemaToJson(GraphSchema<LiteralType> schema) {
        Term term = Model.graphSchema(Core::literalType, schema);
        return termToJson(term, "schema");
    }

    private static String encodeGraphToJson(Graph<Literal> graph) {
        Term term = Model.graph(Core::literal, graph);
        return termToJson(term, "graph");
    }

    private static String termToJson(Term term, String description) {
        Either<String, Value> result = Encode.toJson(term);
        return result.accept(new Either.Visitor<String, Value, String>() {
            @Override
            public String visit(Either.Left<String, Value> instance) {
                throw new RuntimeException("Failed to encode " + description + ": " + instance.value);
            }

            @Override
            public String visit(Either.Right<String, Value> instance) {
                return Writer.printJson(instance.value);
            }
        });
    }

    private static void writeJsonFile(Path dir, String filename, String json) throws IOException {
        Path file = dir.resolve(filename);
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        System.out.println("  Wrote " + file);
    }

    // -- Graph modification --

    @FunctionalInterface
    private interface GraphModifier {
        void modify(java.util.Map<Literal, Vertex<Literal>> vertices,
                    java.util.Map<Literal, Edge<Literal>> edges);
    }

    private static Graph<Literal> buildModifiedGraph(GraphModifier modifier) {
        Graph<Literal> base = ExampleGraphs.buildModernGraph();
        java.util.Map<Literal, Vertex<Literal>> vertices = new java.util.HashMap<>(base.vertices);
        java.util.Map<Literal, Edge<Literal>> edges = new java.util.HashMap<>(base.edges);
        modifier.modify(vertices, edges);
        return new Graph<>(vertices, edges);
    }
}
