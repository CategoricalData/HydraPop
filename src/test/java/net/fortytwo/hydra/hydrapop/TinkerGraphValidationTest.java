package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.dsl.Literals;
import hydra.error.pg.InvalidEdgeError;
import hydra.error.pg.InvalidElementPropertyError;
import hydra.error.pg.InvalidGraphEdgeError;
import hydra.error.pg.InvalidGraphError;
import hydra.error.pg.InvalidGraphVertexError;
import hydra.error.pg.InvalidPropertyError;
import hydra.error.pg.InvalidVertexError;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.util.Optional;
import hydra.validate.Pg;
import hydra.validation.ValidationResult;

import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

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

    private Optional<InvalidGraphError<Literal>> validate(TinkerGraph gremlinGraph) {
        Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(gremlinGraph,
                ExampleGraphs::objectToLiteral);
        return validate(hydraGraph);
    }

    // Runs validation and collapses the ValidationResult to its first error (if any).
    private Optional<InvalidGraphError<Literal>> validate(Graph<Literal> hydraGraph) {
        ValidationResult<InvalidGraphError<Literal>> result = Pg.validateGraph(
                Pg.defaultPgProfile(),
                new ValidationResult<>(List.of(), List.of()),
                ExampleGraphs::checkLiteral, schema, hydraGraph);
        return result.errors.isEmpty()
                ? Optional.none()
                : Optional.given(result.errors.get(0));
    }

    // The unmodified Modern graph should validate successfully.
    @Test
    public void testValidGraph() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            assertValid(validate(g));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex missing a required property ("name") should fail validation.
    @Test
    public void testMissingRequiredProperty() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.vertices(1).next().property("name").remove();
            assertInvalid(validate(g), isVertexPropertyError(isMissingValue()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex with an id type that doesn't match the schema should fail validation.
    @Test
    public void testWrongIdType() {
        try (TinkerGraph g = TinkerGraph.open()) {
            g.addVertex(T.id, "not-an-int", T.label, "person", "name", "Wrong");
            assertInvalid(validate(g), isVertexIdError());
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

            assertInvalid(validate(modified), isEdgeError(isVertexNotFound()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A vertex with a label not defined in the schema should fail validation.
    @Test
    public void testUnexpectedVertexLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.addVertex(T.id, 99, T.label, "robot", "name", "Marvin");
            assertInvalid(validate(g), isVertexLabelError());
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
            assertInvalid(validate(g), isEdgeLabelError());
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
            assertInvalid(validate(g), isVertexPropertyError(isInvalidValue()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A property key not defined in the schema should fail validation.
    @Test
    public void testUnexpectedPropertyKey() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            g.vertices(1).next().property("favoriteColor", "blue");
            assertInvalid(validate(g), isVertexPropertyError(isUnexpectedKey()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // An edge whose in-vertex has the wrong label should fail validation.
    @Test
    public void testWrongInVertexLabel() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            Vertex marko = g.vertices(1).next();
            Vertex josh = g.vertices(4).next();
            // "created" expects person -> software, but josh is a person
            marko.addEdge("created", josh, T.id, 99, "weight", 0.5d);
            assertInvalid(validate(g), isEdgeError(isWrongInVertexLabel()));
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
            assertInvalid(validate(g), isEdgeError(isWrongOutVertexLabel()));
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
            assertInvalid(validate(g), isEdgePropertyError(isMissingValue()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // A graph with multiple validation issues. Hydra's validateGraph returns only the first
    // error encountered, which (depending on traversal order) could be either of the introduced issues.
    @Test
    public void testMultipleIssuesReportsFirst() {
        try (TinkerGraph g = TinkerFactory.createModern()) {
            // Issue 1: unexpected vertex label
            g.addVertex(T.id, 99, T.label, "robot", "name", "Marvin");
            // Issue 2: edge missing required "weight" property
            Vertex marko = g.vertices(1).next();
            Vertex lop = g.vertices(3).next();
            marko.addEdge("created", lop, T.id, 98);

            Optional<InvalidGraphError<Literal>> result = validate(g);
            assertTrue(result.isGiven(), "Expected validation error but graph was valid");
            // The first error reported is one of the two
            InvalidGraphError<Literal> err = result.fromGiven();
            assertTrue(isVertexLabelError().test(err) || isEdgePropertyError(isMissingValue()).test(err),
                    "Expected first-encountered error to be vertex-label or edge-property; got: " + err);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -- Assertion helpers ----------------------------------------------------

    private static void assertValid(Optional<InvalidGraphError<Literal>> result) {
        assertFalse(result.isGiven(),
                "Expected valid graph but got: " + (result.isGiven() ? result.fromGiven() : ""));
    }

    private static void assertInvalid(Optional<InvalidGraphError<Literal>> result,
                                      Predicate<InvalidGraphError<Literal>> expected) {
        assertTrue(result.isGiven(), "Expected validation error but graph was valid");
        InvalidGraphError<Literal> err = result.fromGiven();
        assertTrue(expected.test(err),
                "Validation error did not match expected predicate; got: " + err);
    }

    // -- Predicate combinators on the typed PG error sums --------------------

    private static Predicate<InvalidGraphError<Literal>> isVertexError(Predicate<InvalidVertexError> inner) {
        return err -> err instanceof InvalidGraphError.Vertex
                && inner.test(((InvalidGraphError.Vertex<Literal>) err).value.error);
    }

    private static Predicate<InvalidGraphError<Literal>> isEdgeError(Predicate<InvalidEdgeError> inner) {
        return err -> err instanceof InvalidGraphError.Edge
                && inner.test(((InvalidGraphError.Edge<Literal>) err).value.error);
    }

    private static Predicate<InvalidGraphError<Literal>> isVertexIdError() {
        return isVertexError(v -> v instanceof InvalidVertexError.Id);
    }

    private static Predicate<InvalidGraphError<Literal>> isVertexLabelError() {
        return isVertexError(v -> v instanceof InvalidVertexError.Label);
    }

    private static Predicate<InvalidGraphError<Literal>> isVertexPropertyError(
            Predicate<InvalidPropertyError> inner) {
        return isVertexError(v -> v instanceof InvalidVertexError.Property
                && inner.test(((InvalidVertexError.Property) v).value.error));
    }

    private static Predicate<InvalidGraphError<Literal>> isEdgeLabelError() {
        return isEdgeError(e -> e instanceof InvalidEdgeError.Label);
    }

    private static Predicate<InvalidGraphError<Literal>> isEdgePropertyError(
            Predicate<InvalidPropertyError> inner) {
        return isEdgeError(e -> e instanceof InvalidEdgeError.Property
                && inner.test(((InvalidEdgeError.Property) e).value.error));
    }

    private static Predicate<InvalidEdgeError> isWrongInVertexLabel() {
        return e -> e instanceof InvalidEdgeError.InVertexLabel;
    }

    private static Predicate<InvalidEdgeError> isWrongOutVertexLabel() {
        return e -> e instanceof InvalidEdgeError.OutVertexLabel;
    }

    private static Predicate<InvalidEdgeError> isVertexNotFound() {
        return e -> e instanceof InvalidEdgeError.InVertexNotFound
                || e instanceof InvalidEdgeError.OutVertexNotFound;
    }

    private static Predicate<InvalidPropertyError> isInvalidValue() {
        return p -> p instanceof InvalidPropertyError.InvalidValue;
    }

    private static Predicate<InvalidPropertyError> isMissingValue() {
        return p -> p instanceof InvalidPropertyError.MissingRequired;
    }

    private static Predicate<InvalidPropertyError> isUnexpectedKey() {
        return p -> p instanceof InvalidPropertyError.UnexpectedKey;
    }
}
