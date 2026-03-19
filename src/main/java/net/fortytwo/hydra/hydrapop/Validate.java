package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.pg.validation.Validation;
import hydra.util.Maybe;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

/**
 * Convenience methods for validating TinkerPop graphs against a Hydra GraphSchema.
 */
public class Validate {

    private Validate() {
    }

    /**
     * The result of validating a graph against a schema.
     */
    public static class Result {
        private final String error;

        private Result(String error) {
            this.error = error;
        }

        /** Returns true if the graph is valid. */
        public boolean isValid() {
            return error == null;
        }

        /** Returns the error message, or null if valid. */
        public String getError() {
            return error;
        }

        @Override
        public String toString() {
            return error == null ? "VALID" : "INVALID - " + error;
        }
    }

    /**
     * Validates a TinkerPop graph against a Hydra GraphSchema.
     *
     * <p>Converts the TinkerPop graph to a Hydra graph using
     * {@link HydraGremlinBridge#objectToLiteral} and validates it using
     * {@link HydraGremlinBridge#checkLiteral} and {@link HydraGremlinBridge#showLiteral}.
     *
     * @param schema the graph schema to validate against
     * @param gremlinGraph the TinkerPop graph to validate
     * @return a {@link Result} whose {@code toString()} is either "VALID" or "INVALID - ..."
     */
    public static Result validate(
            GraphSchema<LiteralType> schema,
            org.apache.tinkerpop.gremlin.structure.Graph gremlinGraph) {
        Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(
                gremlinGraph, HydraGremlinBridge::objectToLiteral);
        Maybe<String> result = Validation.validateGraph(
                HydraGremlinBridge::checkLiteral,
                HydraGremlinBridge::showLiteral,
                schema,
                hydraGraph);
        return new Result(result.isJust() ? result.fromJust() : null);
    }

    /**
     * Validates a TinkerPop graph via a traversal source against a Hydra GraphSchema.
     * Works with both local and remote graph connections.
     *
     * @param schema the graph schema to validate against
     * @param g the traversal source to read the graph from
     * @return a {@link Result} whose {@code toString()} is either "VALID" or "INVALID - ..."
     */
    public static Result validate(
            GraphSchema<LiteralType> schema,
            GraphTraversalSource g) {
        Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(
                g, HydraGremlinBridge::objectToLiteral);
        Maybe<String> result = Validation.validateGraph(
                HydraGremlinBridge::checkLiteral,
                HydraGremlinBridge::showLiteral,
                schema,
                hydraGraph);
        return new Result(result.isJust() ? result.fromJust() : null);
    }
}
