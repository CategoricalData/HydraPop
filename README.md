# HydraPop: translingual extensions for Apache TinkerPop

HydraPop connects [Apache TinkerPop](https://tinkerpop.apache.org/) with
the graph programming language [Hydra](https://github.com/CategoricalData/hydra).
Hydra is a language for *translingual programming*, meaning that Hydra code can be written in multiple languages,
and also compiled to multiple languages.
This repository currently deals with TinkerPop's Java API, but Hydra also has heads (complete implementations) in
Python and Haskell, with others on the way.
Hydra is being explored as a means for providing validation logic and other functionality in a way that is accessible
to each [Gremlin language variant](https://tinkerpop.apache.org/docs/current/reference/#gremlin-drivers-variants), and guaranteed to be consistent across all of them.

## Validation

HydraPop can validate a TinkerPop graph against a Hydra `GraphSchema` using Hydra's built-in support for
[property graph validation](https://github.com/CategoricalData/hydra/tree/main/hydra-ext/demos/validatepg).
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

## Gremlin Console example

You can validate a TinkerPop graph interactively from the
[Gremlin Console](https://tinkerpop.apache.org/docs/current/reference/#gremlin-console).

### Setup

Build the project and collect the jars needed by the console:

```bash
./gradlew consoleLibs
```

Then copy `build/console-libs/*.jar` into the Gremlin Console's `lib/` directory:

```bash
cp build/console-libs/*.jar /path/to/apache-tinkerpop-gremlin-console/lib/
```

### Session

```groovy
gremlin> import hydra.core.*
gremlin> import hydra.dsl.*
gremlin> import hydra.pg.dsl.Graphs
gremlin> import hydra.pg.model.*
gremlin> import hydra.pg.validation.Validation
gremlin> import hydra.reflect.Reflect
gremlin> import net.fortytwo.hydra.hydrapop.HydraGremlinBridge

// Define a schema for the Modern graph
gremlin> personType = Graphs.vertexType("person", LiteralTypes.int32()).property("name", LiteralTypes.string(), true).property("age", LiteralTypes.int32(), false).build()
gremlin> softwareType = Graphs.vertexType("software", LiteralTypes.int32()).property("name", LiteralTypes.string(), true).property("lang", LiteralTypes.string(), true).build()
gremlin> knowsType = Graphs.edgeType("knows", LiteralTypes.int32(), "person", "person").property("weight", LiteralTypes.float64(), true).build()
gremlin> createdType = Graphs.edgeType("created", LiteralTypes.int32(), "person", "software").property("weight", LiteralTypes.float64(), true).build()
gremlin> vtypes = [:]; vtypes[personType.label] = personType; vtypes[softwareType.label] = softwareType
gremlin> etypes = [:]; etypes[knowsType.label] = knowsType; etypes[createdType.label] = createdType
gremlin> schema = new GraphSchema(vtypes, etypes)

// Convert a value from TinkerPop to a Hydra Literal
gremlin> objectToLiteral = { obj ->
           if (obj instanceof String) return Literals.string(obj)
           if (obj instanceof Integer) return Literals.int32(obj)
           if (obj instanceof Double) return Literals.float64(obj)
           throw new RuntimeException("unsupported: " + obj.getClass())
         } as java.util.function.Function

// Set up validation functions
gremlin> checkLiteral = { type -> { value ->
           def actual = Reflect.literalType(value)
           type.equals(actual) ? hydra.util.Maybe.nothing()
             : hydra.util.Maybe.just("expected " + LiteralTypes.showLiteralType(type) + ", got " + LiteralTypes.showLiteralType(actual))
         } as java.util.function.Function } as java.util.function.Function
gremlin> showLiteral = { lit -> Literals.showLiteral(lit) } as java.util.function.Function

// Load the Modern graph and validate -- should pass
gremlin> graph = TinkerFactory.createModern()
gremlin> hydraGraph = HydraGremlinBridge.gremlinToHydra(graph, objectToLiteral)
gremlin> Validation.validateGraph(checkLiteral, showLiteral, schema, hydraGraph)
==>Nothing

// Now break it: remove a required property
gremlin> graph.vertices(1).next().property("name").remove()
gremlin> hydraGraph = HydraGremlinBridge.gremlinToHydra(graph, objectToLiteral)
gremlin> Validation.validateGraph(checkLiteral, showLiteral, schema, hydraGraph)
==>Just "Invalid vertex with id integer:int32:1: Invalid property: Missing value for: name"
```
