package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.error.pg.InvalidGraphError;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.util.Maybe;
import hydra.validate.Pg;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

/**
 * Convenience methods for validating TinkerPop graphs against a Hydra GraphSchema.
 */
public class Validate {

    private Validate() {
    }

    /**
     * The result of validating a graph against a schema.
     *
     * <p>In 0.15+, errors are typed ({@link InvalidGraphError}) rather than bare strings;
     * the result retains the typed value alongside a human-readable rendering.
     */
    public static class Result {
        private final Maybe<InvalidGraphError<Literal>> error;

        private Result(Maybe<InvalidGraphError<Literal>> error) {
            this.error = error;
        }

        /** Returns true if the graph is valid. */
        public boolean isValid() {
            return !error.isJust();
        }

        /** Returns the typed error, or {@link Maybe#nothing()} if valid. */
        public Maybe<InvalidGraphError<Literal>> getError() {
            return error;
        }

        @Override
        public String toString() {
            return error.isJust() ? "INVALID - " + error.fromJust() : "VALID";
        }
    }

    /**
     * Validates a TinkerPop graph against a Hydra GraphSchema.
     *
     * <p>Converts the TinkerPop graph to a Hydra graph using
     * {@link HydraGremlinBridge#objectToLiteral} and validates it using
     * {@link HydraGremlinBridge#checkLiteral}. Returns at most one error
     * (the first encountered).
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
        Maybe<InvalidGraphError<Literal>> result = Pg.validateGraph(
                HydraGremlinBridge::checkLiteral,
                schema,
                hydraGraph);
        return new Result(result);
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
        Maybe<InvalidGraphError<Literal>> result = Pg.validateGraph(
                HydraGremlinBridge::checkLiteral,
                schema,
                hydraGraph);
        return new Result(result);
    }
}
