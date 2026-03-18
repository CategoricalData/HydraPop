package net.fortytwo.hydra.hydrapop;

import hydra.core.FloatValue;
import hydra.core.IntegerValue;
import hydra.core.Literal;
import hydra.core.LiteralType;
import hydra.dsl.LiteralTypes;
import hydra.dsl.Literals;
import hydra.pg.dsl.Graphs;
import hydra.reflect.Reflect;
import hydra.util.Maybe;
import hydra.pg.model.Edge;
import hydra.pg.model.EdgeLabel;
import hydra.pg.model.EdgeType;
import hydra.pg.model.Graph;
import hydra.pg.model.GraphSchema;
import hydra.pg.model.Vertex;
import hydra.pg.model.VertexLabel;
import hydra.pg.model.VertexType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Example property graphs for testing.
 *
 * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory</a>
 */
class ExampleGraphs {

    private ExampleGraphs() {
    }

    /**
     * Builds a GraphSchema for the TinkerPop "Modern" graph using the Hydra PG DSL.
     *
     * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory.createModern()</a>
     */
    static GraphSchema<LiteralType> buildModernGraphSchema() {
        VertexType<LiteralType> personType = Graphs.<LiteralType>vertexType("person", LiteralTypes.int32())
                .property("name", LiteralTypes.string(), true)
                .property("age", LiteralTypes.int32(), false)
                .build();

        VertexType<LiteralType> softwareType = Graphs.<LiteralType>vertexType("software", LiteralTypes.int32())
                .property("name", LiteralTypes.string(), true)
                .property("lang", LiteralTypes.string(), true)
                .build();

        EdgeType<LiteralType> knowsType = Graphs.<LiteralType>edgeType("knows", LiteralTypes.int32(), "person", "person")
                .property("weight", LiteralTypes.float64(), true)
                .build();

        EdgeType<LiteralType> createdType = Graphs.<LiteralType>edgeType("created", LiteralTypes.int32(), "person", "software")
                .property("weight", LiteralTypes.float64(), true)
                .build();

        Map<VertexLabel, VertexType<LiteralType>> vertexTypes = new HashMap<>();
        vertexTypes.put(personType.label, personType);
        vertexTypes.put(softwareType.label, softwareType);

        Map<EdgeLabel, EdgeType<LiteralType>> edgeTypes = new HashMap<>();
        edgeTypes.put(knowsType.label, knowsType);
        edgeTypes.put(createdType.label, createdType);

        return new GraphSchema<>(vertexTypes, edgeTypes);
    }

    /**
     * Builds the TinkerPop "Modern" graph using the Hydra PG DSL.
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

        Vertex<Literal> marko = Graphs.<Literal>vertex("person", id1)
                .property("name", Literals.string("marko"))
                .property("age", Literals.int32(29))
                .build();
        Vertex<Literal> vadas = Graphs.<Literal>vertex("person", id2)
                .property("name", Literals.string("vadas"))
                .property("age", Literals.int32(27))
                .build();
        Vertex<Literal> lop = Graphs.<Literal>vertex("software", id3)
                .property("name", Literals.string("lop"))
                .property("lang", Literals.string("java"))
                .build();
        Vertex<Literal> josh = Graphs.<Literal>vertex("person", id4)
                .property("name", Literals.string("josh"))
                .property("age", Literals.int32(32))
                .build();
        Vertex<Literal> ripple = Graphs.<Literal>vertex("software", id5)
                .property("name", Literals.string("ripple"))
                .property("lang", Literals.string("java"))
                .build();
        Vertex<Literal> peter = Graphs.<Literal>vertex("person", id6)
                .property("name", Literals.string("peter"))
                .property("age", Literals.int32(35))
                .build();

        Edge<Literal> knows1 = Graphs.<Literal>edge("knows", Literals.int32(7),
                        id1, id2)
                .property("weight", Literals.float64(0.5d))
                .build();
        Edge<Literal> knows2 = Graphs.<Literal>edge("knows", Literals.int32(8),
                        id1, id4)
                .property("weight", Literals.float64(1.0d))
                .build();
        Edge<Literal> created1 = Graphs.<Literal>edge("created", Literals.int32(9),
                        id1, id3)
                .property("weight", Literals.float64(0.4d))
                .build();
        Edge<Literal> created2 = Graphs.<Literal>edge("created", Literals.int32(10),
                        id4, id5)
                .property("weight", Literals.float64(1.0d))
                .build();
        Edge<Literal> created3 = Graphs.<Literal>edge("created", Literals.int32(11),
                        id4, id3)
                .property("weight", Literals.float64(0.4d))
                .build();
        Edge<Literal> created4 = Graphs.<Literal>edge("created", Literals.int32(12),
                        id6, id3)
                .property("weight", Literals.float64(0.2d))
                .build();

        Map<Literal, Vertex<Literal>> vertices = new HashMap<>();
        for (Vertex<Literal> v : Arrays.asList(marko, vadas, lop, josh, ripple, peter)) {
            vertices.put(v.id, v);
        }

        Map<Literal, Edge<Literal>> edges = new HashMap<>();
        for (Edge<Literal> e : Arrays.asList(knows1, knows2, created1, created2, created3, created4)) {
            edges.put(e.id, e);
        }

        return new Graph<>(vertices, edges);
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
        if (obj instanceof String) {
            return Literals.string((String) obj);
        } else if (obj instanceof Integer) {
            return Literals.int32((Integer) obj);
        } else if (obj instanceof Float) {
            return Literals.float32((Float) obj);
        } else if (obj instanceof Double) {
            return Literals.float64((Double) obj);
        }
        throw new UnsupportedOperationException("Unsupported object type: " + obj.getClass());
    }

    /**
     * Checks whether a Literal value matches a LiteralType.
     */
    static java.util.function.Function<Literal, Maybe<String>> checkLiteral(LiteralType type) {
        return value -> {
            LiteralType actual = Reflect.literalType(value);
            if (type.equals(actual)) {
                return Maybe.nothing();
            }
            return Maybe.just("expected " + LiteralTypes.showLiteralType(type)
                    + ", got " + LiteralTypes.showLiteralType(actual));
        };
    }

    /**
     * Displays a Literal value as a human-readable string.
     */
    static String showLiteral(Literal lit) {
        return Literals.showLiteral(lit);
    }
}
