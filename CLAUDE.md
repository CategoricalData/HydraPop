# LLM quickstart guide for HydraPop

HydraPop is closely related to [Hydra](https://github.com/CategoricalData/hydra) and inherits its LLM workflows.
See [Hydra's CLAUDE.md](https://github.com/CategoricalData/hydra/blob/main/CLAUDE.md)
for the session startup procedure, branch plan conventions, and general Hydra architecture.

## What is HydraPop?

HydraPop connects [Apache TinkerPop](https://tinkerpop.apache.org/) (Gremlin) with
[Hydra](https://github.com/CategoricalData/hydra). It demonstrates translingual
property graph validation: the same Hydra-generated validation logic runs in both
Java and Python against the same data.

## Project structure

```
HydraPop/
  src/
    main/
      java/net/fortytwo/hydra/hydrapop/
        HydraGremlinBridge.java     # Bidirectional Hydra <-> TinkerPop conversion
        ExampleGraphs.java          # Source of truth: Modern graph schema + data
        GenerateExampleData.java    # Encodes Java data as JSON for Python
      python/hydrapop/
        decode.py                   # JSON -> Hydra PG model decoders
        dsl/pg.py                   # Python PG DSL (vertex_type, edge_type, etc.)
        gremlin_bridge.py           # gremlinpython -> Hydra PG model conversion
        validate.py                 # check_literal / show_literal / validate()
    test/
      java/net/fortytwo/hydra/hydrapop/
        HydraGremlinBridgeTest.java   # Bridge tests (conversion + round-trips)
        TinkerGraphValidationTest.java # Validation tests via TinkerPop
      python/hydrapop/
        test_validation.py            # Validation tests via JSON interchange
    gen-main/
      json/                         # Generated: schema + graph JSON files
  build.gradle                      # Java build (Gradle)
  settings.gradle                   # Gradle settings (project name only)
  pyproject.toml                    # Python build (pixi + hatchling + pytest)
```

### Generated vs hand-written

Following Hydra's convention:

- **`src/main/`** -- Hand-written code. Edit freely.
- **`src/gen-main/`** -- Generated artifacts. Regenerate with `./gradlew generateExampleData`.
  Do not edit by hand.

The JSON files in `src/gen-main/json/` are produced by `GenerateExampleData`, which
encodes the Java-defined schema and graphs as Hydra Terms and serializes them to JSON.

## Key classes

### Java

- **`ExampleGraphs`** -- Source of truth for all example data. Builds the
  TinkerPop Modern graph schema (`GraphSchema<LiteralType>`) and graph
  (`Graph<Literal>`) using Hydra's PG DSL. Also provides `literalToObject`,
  `objectToLiteral`, `checkLiteral`, and `showLiteral` helpers.

- **`hydra/pg/dsl/`** -- Vendored copy of the fluent PG DSL helpers
  (`Graphs`, `Queries`, and the `*Builder` classes) from
  packages/hydra-java/src/main/java/hydra/pg/dsl/ in the Hydra 0.15.0 source
  tree. The Hydra 0.15 release did not publish these classes in any Maven
  artifact (`hydra-java:0.15.0` ships only the Java coder; `hydra-pg:0.15.0`
  ships the model + validation but not the hand-written DSL helpers).
  When Hydra publishes `hydra-pg-dsl` separately (planned for 0.16), this
  vendored copy can be removed in favor of that dependency.

- **`HydraGremlinBridge`** -- Bidirectional conversion between Hydra and
  TinkerPop property graphs. Generic over the value type.

- **`Validate`** -- Convenience method for validating a TinkerPop graph
  against a Hydra `GraphSchema`. Wraps the `gremlinToHydra` + `validateGraph`
  pipeline.

- **`GenerateExampleData`** -- Encodes example data as JSON via Hydra's term
  encoder (`hydra.encode.pg.model.Model`) and JSON serializer
  (`hydra.json.encode.Encode`). Produces `src/gen-main/json/*.json`.

### Python

- **`hydrapop.decode`** -- Decodes JSON into `hydra.pg.model` objects
  (`GraphSchema`, `Graph`, `Vertex`, `Edge`, `Literal`, etc.). Adapted from
  the Hydra validatepg demo.

- **`hydrapop.dsl.pg`** -- Python DSL for building Hydra PG schemas. Mirrors
  the Java `hydra.pg.dsl.Graphs` builder API with `vertex_type()`,
  `edge_type()`, `graph_schema()`, and literal type shortcuts.

- **`hydrapop.validate`** -- Provides the `check_literal` callback for
  `hydra.validate.pg.validate_graph` (typed `InvalidValueError` in 0.15+),
  plus a convenience `validate(schema, g)` function and `Result` class for
  interactive use. The 0.14 `show_literal` callback is no longer required
  by the kernel API but is retained for callers that want a human-readable
  rendering of a Literal.

- **`hydrapop.gremlin_bridge`** -- Converts a TinkerPop graph (via
  gremlinpython `GraphTraversalSource`) into a Hydra `Graph[Literal]`.
  Python equivalent of Java's `HydraGremlinBridge.gremlinToHydra()`.
  Requires `gremlinpython` and a running Gremlin Server.

## Build and test

### Java

```bash
./gradlew build                    # Compile + test
./gradlew test                     # Tests only
./gradlew generateExampleData      # Regenerate JSON from Java data
./gradlew consoleLibs              # Package JARs for Gremlin Console
```

Requires: **Java 17+**, Gradle 8.12.1 (wrapper included)

### Python

```bash
pixi install                       # Install dependencies
pixi run test                      # Run pytest
pixi run lint                      # Run ruff
```

Requires: [pixi](https://pixi.sh/), Python 3.12+

## Dependencies

### Java

| Dependency | Version | Scope |
|------------|---------|-------|
| hydra-java | 0.15.0  | api |
| gremlin-core | 3.8.0 | api |
| tinkergraph-gremlin | 3.8.0 | test |
| JUnit 5 | 5.9.2 | test |

### Python

The conda-forge packages `hydra-kernel` and `hydra-pg` (split out from the
0.14 rolled-up `hydra-python` in Hydra 0.15) provide core Hydra types and
the PG model + validation modules (`hydra.pg.model`, `hydra.validate.pg`).

The `gremlinpython` conda package (>= 3.8.0) provides the Python Gremlin
driver, used by `hydrapop.gremlin_bridge` to connect to a Gremlin Server.

## Translingual data interchange

Java is the source of truth for example data. The interchange flow is:

1. `ExampleGraphs` (Java) defines schemas and graphs using Hydra's PG DSL
2. `GenerateExampleData` (Java) encodes them as Hydra Terms, serializes to JSON
3. `hydrapop.decode` (Python) loads the JSON, decodes into `hydra.pg.model` objects
4. Both Java and Python tests validate the same data using the same Hydra
   validation logic (generated by Hydra for each language)

This approach ensures that validation results are identical across languages
without requiring a running TinkerPop server on the Python side.
