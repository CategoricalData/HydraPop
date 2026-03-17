package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.dsl.Literals;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.pg.validation.Validation;
import hydra.util.Maybe;

import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates TinkerPop graphs against a Hydra GraphSchema using the bridge.
 * Each test loads the TinkerPop Modern graph via TinkerFactory, optionally modifies it
 * to introduce a specific validation error, converts it to a Hydra graph, and validates.
 *
 * <p>Note: Hydra's validateGraph returns only the first error encountered.
 *
 * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory</a>
 */
public class TinkerGraphValidationTest {

    private final GraphSchema<LiteralType> schema = ExampleGraphs.buildModernGraphSchema();

    private Maybe<String> validate(TinkerGraph gremlinGraph) {
        Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(gremlinGraph,
                ExampleGraphs::objectToLiteral);
        return Validation.validateGraph(
                ExampleGraphs::checkLiteral,
                ExampleGraphs::showLiteral,
                schema,
                hydraGraph);
    }

    // The unmodified Modern graph should validate successfully.
    @Test
    public void testValidGraph() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Maybe<String> result = validate(g);
            assertValid(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex missing a required property ("name") should fail validation.
    @Test
    public void testMissingRequiredProperty() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.vertices(1).next().property("name").remove();
            Maybe<String> result = validate(g);
            assertInvalid(result, "Missing value for");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex with an id type that doesn't match the schema should fail validation.
    @Test
    public void testWrongIdType() {
        // Use a fresh TinkerGraph that accepts any id type
        try (TinkerGraph g = TinkerGraph.open()) {
            // Recreate a minimal graph with a string id where int32 is expected
            g.addVertex(T.id, "not-an-int", T.label, "person", "name", "Wrong");
            Maybe<String> result = validate(g);
            assertInvalid(result, "Invalid id");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge referencing a non-existent vertex should fail validation.
    // TinkerPop cascades vertex removal to edges, so we construct the dangling reference
    // at the Hydra level by modifying the converted graph.
    @Test
    public void testUnknownEdgeEndpoint() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(g,
                    ExampleGraphs::objectToLiteral);

            // Remove vadas (id=2) from vertices, leaving the knows edge (id=7) dangling
            java.util.Map<Literal, hydra.pg.model.Vertex<Literal>> vertices =
                    new java.util.HashMap<>(hydraGraph.vertices);
            vertices.remove(Literals.int32(2));
            Graph<Literal> modified = new Graph<>(vertices, hydraGraph.edges);

            Maybe<String> result = Validation.validateGraph(
                    ExampleGraphs::checkLiteral,
                    ExampleGraphs::showLiteral,
                    schema,
                    modified);
            assertInvalid(result, "does not exist");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex with a label not defined in the schema should fail validation.
    @Test
    public void testUnexpectedVertexLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.addVertex(T.id, 99, T.label, "robot", "name", "Bender");
            Maybe<String> result = validate(g);
            assertInvalid(result, "Unexpected label");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge with a label not defined in the schema should fail validation.
    @Test
    public void testUnexpectedEdgeLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Vertex marko = g.vertices(1).next();
            Vertex josh = g.vertices(4).next();
            marko.addEdge("manages", josh, T.id, 99);
            Maybe<String> result = validate(g);
            assertInvalid(result, "Unexpected label");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A property value with the wrong type should fail validation.
    @Test
    public void testPropertyValueTypeMismatch() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            // Set "name" to an integer instead of a string
            g.vertices(1).next().property("name", 999);
            Maybe<String> result = validate(g);
            assertInvalid(result, "Invalid value");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A property key not defined in the schema should fail validation.
    @Test
    public void testUnexpectedPropertyKey() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.vertices(1).next().property("favoriteColor", "blue");
            Maybe<String> result = validate(g);
            assertInvalid(result, "Unexpected key");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge whose in-vertex has the wrong label should fail validation.
    @Test
    public void testWrongInVertexLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            // Add a "worksAt"-style edge between two persons (should be person -> software for "created")
            Vertex marko = g.vertices(1).next();
            Vertex josh = g.vertices(4).next();
            // "created" expects person -> software, but josh is a person
            marko.addEdge("created", josh, T.id, 99, "weight", 0.5d);
            Maybe<String> result = validate(g);
            assertInvalid(result, "Wrong in-vertex label");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge whose out-vertex has the wrong label should fail validation.
    @Test
    public void testWrongOutVertexLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Vertex lop = g.vertices(3).next();
            Vertex ripple = g.vertices(5).next();
            // "created" expects person -> software, but lop (out-vertex) is software
            lop.addEdge("created", ripple, T.id, 99, "weight", 0.5d);
            Maybe<String> result = validate(g);
            assertInvalid(result, "Wrong out-vertex label");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge missing a required property should fail validation.
    @Test
    public void testMissingRequiredEdgeProperty() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Vertex marko = g.vertices(1).next();
            Vertex lop = g.vertices(3).next();
            // "created" requires "weight", but we omit it
            marko.addEdge("created", lop, T.id, 99);
            Maybe<String> result = validate(g);
            assertInvalid(result, "Missing value for");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A graph with multiple validation issues (unexpected vertex label + missing required edge property).
    // Hydra's validateGraph returns only the first error encountered.
    @Test
    public void testMultipleIssuesReportsFirst() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            // Issue 1: unexpected vertex label
            g.addVertex(T.id, 99, T.label, "robot", "name", "Bender");
            // Issue 2: edge missing required "weight" property
            Vertex marko = g.vertices(1).next();
            Vertex lop = g.vertices(3).next();
            marko.addEdge("created", lop, T.id, 98);

            Maybe<String> result = validate(g);
            assertTrue(result.isJust(), "Expected validation error but graph was valid");
            // Only one error is reported, not both
            String error = result.fromJust();
            assertTrue(error.contains("Unexpected label") || error.contains("Missing value for"),
                    "Expected first-encountered error but got: " + error);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertValid(Maybe<String> result) {
        assertFalse(result.isJust(),
                "Expected valid graph but got: " + (result.isJust() ? result.fromJust() : ""));
    }

    private static void assertInvalid(Maybe<String> result, String expectedSubstring) {
        assertTrue(result.isJust(), "Expected validation error but graph was valid");
        assertTrue(result.fromJust().contains(expectedSubstring),
                "Expected error containing \"" + expectedSubstring + "\" but got: " + result.fromJust());
    }
}
