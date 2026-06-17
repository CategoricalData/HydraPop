package net.fortytwo.hydra.hydrapop;

import hydra.core.FloatValue;
import hydra.core.IntegerValue;
import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.dsl.LiteralTypes;
import hydra.dsl.Literals;
import hydra.Reflect;
import hydra.util.Optional;
import hydra.pg.model.Edge;
import hydra.pg.model.EdgeLabel;
import hydra.pg.model.EdgeType;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.pg.model.PropertyKey;
import hydra.pg.model.PropertyType;
import hydra.pg.model.Vertex;
import hydra.pg.model.VertexLabel;
import hydra.pg.model.VertexType;

import hydra.util.PersistentMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Example property graphs for testing.
 *
 * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory</a>
 */
public class ExampleGraphs {

    private ExampleGraphs() {
    }

    /**
     * Builds a GraphSchema for the TinkerPop "Modern" graph.
     *
     * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory.createModern()</a>
     */
    public static GraphSchema<LiteralType> buildModernGraphSchema() {
        VertexType<LiteralType> personType = VertexType.<LiteralType>builder()
                .label(new VertexLabel("person"))
                .id(LiteralTypes.int32())
                .properties(Arrays.asList(
                        propertyType("name", LiteralTypes.string(), true),
                        propertyType("age", LiteralTypes.int32(), false)))
                .build();

        VertexType<LiteralType> softwareType = VertexType.<LiteralType>builder()
                .label(new VertexLabel("software"))
                .id(LiteralTypes.int32())
                .properties(Arrays.asList(
                        propertyType("name", LiteralTypes.string(), true),
                        propertyType("lang", LiteralTypes.string(), true)))
                .build();

        EdgeType<LiteralType> knowsType = EdgeType.<LiteralType>builder()
                .label(new EdgeLabel("knows"))
                .id(LiteralTypes.int32())
                .out(new VertexLabel("person"))
                .in(new VertexLabel("person"))
                .properties(Arrays.asList(propertyType("weight", LiteralTypes.float64(), true)))
                .build();

        EdgeType<LiteralType> createdType = EdgeType.<LiteralType>builder()
                .label(new EdgeLabel("created"))
                .id(LiteralTypes.int32())
                .out(new VertexLabel("person"))
                .in(new VertexLabel("software"))
                .properties(Arrays.asList(propertyType("weight", LiteralTypes.float64(), true)))
                .build();

        Map<VertexLabel, VertexType<LiteralType>> vertexTypes = new HashMap<>();
        vertexTypes.put(personType.label, personType);
        vertexTypes.put(softwareType.label, softwareType);

        Map<EdgeLabel, EdgeType<LiteralType>> edgeTypes = new HashMap<>();
        edgeTypes.put(knowsType.label, knowsType);
        edgeTypes.put(createdType.label, createdType);

        return new GraphSchema<>(PersistentMap.fromMap(vertexTypes), PersistentMap.fromMap(edgeTypes));
    }

    /**
     * Builds the TinkerPop "Modern" graph.
     *
     * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory.createModern()</a>
     */
    static Graph<Literal> buildModernGraph() {
        Literal id1 = Literals.int32(1);
        Literal id2 = Literals.int32(2);
        Literal id3 = Literals.int32(3);
        Literal id4 = Literals.int32(4);
        Literal id5 = Literals.int32(5);
        Literal id6 = Literals.int32(6);

        Vertex<Literal> marko = vertex("person", id1, properties(
                "name", Literals.string("marko"),
                "age", Literals.int32(29)));
        Vertex<Literal> vadas = vertex("person", id2, properties(
                "name", Literals.string("vadas"),
                "age", Literals.int32(27)));
        Vertex<Literal> lop = vertex("software", id3, properties(
                "name", Literals.string("lop"),
                "lang", Literals.string("java")));
        Vertex<Literal> josh = vertex("person", id4, properties(
                "name", Literals.string("josh"),
                "age", Literals.int32(32)));
        Vertex<Literal> ripple = vertex("software", id5, properties(
                "name", Literals.string("ripple"),
                "lang", Literals.string("java")));
        Vertex<Literal> peter = vertex("person", id6, properties(
                "name", Literals.string("peter"),
                "age", Literals.int32(35)));

        Edge<Literal> knows1 = edge("knows", Literals.int32(7), id1, id2,
                properties("weight", Literals.float64(0.5d)));
        Edge<Literal> knows2 = edge("knows", Literals.int32(8), id1, id4,
                properties("weight", Literals.float64(1.0d)));
        Edge<Literal> created1 = edge("created", Literals.int32(9), id1, id3,
                properties("weight", Literals.float64(0.4d)));
        Edge<Literal> created2 = edge("created", Literals.int32(10), id4, id5,
                properties("weight", Literals.float64(1.0d)));
        Edge<Literal> created3 = edge("created", Literals.int32(11), id4, id3,
                properties("weight", Literals.float64(0.4d)));
        Edge<Literal> created4 = edge("created", Literals.int32(12), id6, id3,
                properties("weight", Literals.float64(0.2d)));

        Map<Literal, Vertex<Literal>> vertices = new HashMap<>();
        for (Vertex<Literal> v : Arrays.asList(marko, vadas, lop, josh, ripple, peter)) {
            vertices.put(v.id, v);
        }

        Map<Literal, Edge<Literal>> edges = new HashMap<>();
        for (Edge<Literal> e : Arrays.asList(knows1, knows2, created1, created2, created3, created4)) {
            edges.put(e.id, e);
        }

        return new Graph<>(PersistentMap.fromMap(vertices), PersistentMap.fromMap(edges));
    }

    private static <T> PropertyType<T> propertyType(String key, T value, boolean required) {
        return PropertyType.<T>builder()
                .key(new PropertyKey(key))
                .value(value)
                .required(required)
                .build();
    }

    static Vertex<Literal> vertex(String label, Literal id, Map<PropertyKey, Literal> properties) {
        return Vertex.<Literal>builder()
                .label(new VertexLabel(label))
                .id(id)
                .properties(PersistentMap.fromMap(properties))
                .build();
    }

    static Edge<Literal> edge(
            String label,
            Literal id,
            Literal out,
            Literal in,
            Map<PropertyKey, Literal> properties) {
        return Edge.<Literal>builder()
                .label(new EdgeLabel(label))
                .id(id)
                .out(out)
                .in(in)
                .properties(PersistentMap.fromMap(properties))
                .build();
    }

    static Map<PropertyKey, Literal> properties(Object... keyValues) {
        Map<PropertyKey, Literal> properties = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.put(new PropertyKey((String) keyValues[i]), (Literal) keyValues[i + 1]);
        }
        return properties;
    }

    /**
     * Converts a Hydra Literal to a plain Java object suitable for TinkerPop.
     */
    static Object literalToObject(Literal lit) {
        return lit.accept(new Literal.PartialVisitor<Object>() {
            @Override public Object otherwise(Literal instance) {
                throw new UnsupportedOperationException("Unsupported literal: " + instance);
            }
            @Override public Object visit(Literal.String_ instance) {
                return instance.value;
            }
            @Override public Object visit(Literal.Integer_ instance) {
                return instance.value.accept(new IntegerValue.PartialVisitor<Object>() {
                    @Override public Object otherwise(IntegerValue instance) {
                        throw new UnsupportedOperationException("Unsupported integer: " + instance);
                    }
                    @Override public Object visit(IntegerValue.Int32 instance) {
                        return instance.value;
                    }
                });
            }
            @Override public Object visit(Literal.Float_ instance) {
                return instance.value.accept(new FloatValue.PartialVisitor<Object>() {
                    @Override public Object otherwise(FloatValue instance) {
                        throw new UnsupportedOperationException("Unsupported float: " + instance);
                    }
                    @Override public Object visit(FloatValue.Float32 instance) {
                        return instance.value;
                    }
                    @Override public Object visit(FloatValue.Float64 instance) {
                        return instance.value;
                    }
                });
            }
        });
    }

    /**
     * Converts a plain Java object from TinkerPop back to a Hydra Literal.
     */
    static Literal objectToLiteral(Object obj) {
        return HydraGremlinBridge.objectToLiteral(obj);
    }

    /**
     * Checks whether a Literal value matches a LiteralType.
     */
    static java.util.function.Function<Literal, Optional<hydra.error.pg.InvalidValueError>> checkLiteral(LiteralType type) {
        return HydraGremlinBridge.checkLiteral(type);
    }

    /**
     * Displays a Literal value as a human-readable string.
     */
    static String showLiteral(Literal lit) {
        return HydraGremlinBridge.showLiteral(lit);
    }
}
