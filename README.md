# HydraPop: translingual extensions for Apache TinkerPop

HydraPop connects [Apache TinkerPop](https://tinkerpop.apache.org/) with
the graph programming language [Hydra](https://github.com/CategoricalData/hydra).
Hydra is a language for *translingual programming*, meaning that Hydra code can be written in multiple languages,
and also compiled to multiple languages.
This repository currently deals with TinkerPop's Java API, but Hydra also has heads (complete implementations) in
Python and Haskell, with others on the way.
Hydra is being explored as a means for providing validation logic and other functionality in a way that is accessible
to each Gremlin language variant, and guaranteed to be consistent across all of them.

## Validation

HydraPop can validate a TinkerPop graph against a Hydra `GraphSchema` using Hydra's built-in support for property graph
validation.
The workflow is:

1. Define a schema using the Hydra PG DSL (`hydra.pg.dsl`)
2. Load or construct a TinkerPop graph
3. Convert the TinkerPop graph to a Hydra graph via `HydraGremlinBridge.gremlinToHydra`
4. Validate with `Validation.validateGraph`

The conversion can also happen in the other direction.
The test suite demonstrates the above using TinkerPop's built-in
[Modern graph](https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html). The unmodified graph validates successfully; modified versions exercise each validation error condition:

| Test case | Modification |
|-----------|-------------|
| Valid graph | None |
| Missing required property | Remove a vertex's `name` property |
| Wrong id type | Add a vertex with a string id where int32 is expected |
| Unknown edge endpoint | Remove a vertex that an edge references |
| Unexpected vertex label | Add a vertex with a label not in the schema |
| Unexpected edge label | Add an edge with a label not in the schema |
| Property value type mismatch | Set a string property to an integer |
| Unexpected property key | Add a property not defined in the schema |
| Wrong in-vertex label | Add an edge whose in-vertex has the wrong label |
| Wrong out-vertex label | Add an edge whose out-vertex has the wrong label |
| Missing required edge property | Add an edge without a required property |
| Multiple issues | Introduce two errors; only the first is reported |
