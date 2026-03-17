package net.fortytwo.hydra.hydrapop;

import hydra.core.Literal;
import hydra.pg.dsl.Graphs;
import hydra.pg.model.Edge;
import hydra.pg.model.Graph;
import hydra.pg.model.PropertyKey;
import hydra.pg.model.Vertex;

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerFactory;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link HydraGremlinBridge}, including a round-trip test using the TinkerPop Modern graph.
 *
 * @see <a href="https://tinkerpop.apache.org/javadocs/current/full/org/apache/tinkerpop/gremlin/tinkergraph/structure/TinkerFactory.html">TinkerFactory</a>
 */
public class HydraGremlinBridgeTest {

    // Writes a hand-built Hydra graph to TinkerPop and spot-checks the resulting vertices and edges.
    @Test
    public void testHydraToGremlin() {
        Graph<Literal> hydraGraph = ExampleGraphs.buildModernGraph();

        try (TinkerGraph gremlinGraph = TinkerGraph.open()) {
            HydraGremlinBridge.hydraToGremlin(hydraGraph, gremlinGraph,
                    ExampleGraphs::literalToObject);

            // Check vertex count
            long vertexCount = 0;
            var vertexIter = gremlinGraph.vertices();
            while (vertexIter.hasNext()) { vertexIter.next(); vertexCount++; }
            assertEquals(6, vertexCount);

            // Check edge count
            long edgeCount = 0;
            var edgeIter = gremlinGraph.edges();
            while (edgeIter.hasNext()) { edgeIter.next(); edgeCount++; }
            assertEquals(6, edgeCount);

            // Spot-check marko
            org.apache.tinkerpop.gremlin.structure.Vertex marko = gremlinGraph.vertices(1).next();
            assertEquals("person", marko.label());
            assertEquals("marko", marko.value("name"));
            assertEquals(29, (int) marko.value("age"));

            // Spot-check an edge
            org.apache.tinkerpop.gremlin.structure.Edge knowsEdge = gremlinGraph.edges(7).next();
            assertEquals("knows", knowsEdge.label());
            assertEquals(0.5d, (double) knowsEdge.value("weight"), 0.001d);
            assertEquals(1, knowsEdge.outVertex().id());
            assertEquals(2, knowsEdge.inVertex().id());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Writes a hand-built Hydra graph to TinkerPop, reads it back, and checks element counts.
    @Test
    public void testGremlinToHydra() {
        Graph<Literal> original = ExampleGraphs.buildModernGraph();

        try (TinkerGraph gremlinGraph = TinkerGraph.open()) {
            HydraGremlinBridge.hydraToGremlin(original, gremlinGraph,
                    ExampleGraphs::literalToObject);

            Graph<Literal> roundTripped = HydraGremlinBridge.gremlinToHydra(gremlinGraph,
                    ExampleGraphs::objectToLiteral);

            assertEquals(6, roundTripped.vertices.size());
            assertEquals(6, roundTripped.edges.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Full round-trip: Hydra -> TinkerPop -> Hydra, verifying every vertex and edge field-by-field.
    @Test
    public void testRoundTrip() {
        Graph<Literal> original = ExampleGraphs.buildModernGraph();

        try (TinkerGraph gremlinGraph = TinkerGraph.open()) {
            HydraGremlinBridge.hydraToGremlin(original, gremlinGraph,
                    ExampleGraphs::literalToObject);

            Graph<Literal> roundTripped = HydraGremlinBridge.gremlinToHydra(gremlinGraph,
                    ExampleGraphs::objectToLiteral);

            // Verify all vertices match
            assertEquals(original.vertices.size(), roundTripped.vertices.size());
            for (Map.Entry<Literal, Vertex<Literal>> entry : original.vertices.entrySet()) {
                Vertex<Literal> origVertex = entry.getValue();
                Vertex<Literal> rtVertex = roundTripped.vertices.get(entry.getKey());
                assertNotNull(rtVertex, "Missing vertex: " + entry.getKey());
                assertEquals(origVertex.label, rtVertex.label);
                assertEquals(origVertex.id, rtVertex.id);
                assertEquals(origVertex.properties.size(), rtVertex.properties.size());
                for (Map.Entry<PropertyKey, Literal> prop : origVertex.properties.entrySet()) {
                    assertEquals(prop.getValue(), rtVertex.properties.get(prop.getKey()),
                            "Property mismatch for " + prop.getKey().value);
                }
            }

            // Verify all edges match
            assertEquals(original.edges.size(), roundTripped.edges.size());
            for (Map.Entry<Literal, Edge<Literal>> entry : original.edges.entrySet()) {
                Edge<Literal> origEdge = entry.getValue();
                Edge<Literal> rtEdge = roundTripped.edges.get(entry.getKey());
                assertNotNull(rtEdge, "Missing edge: " + entry.getKey());
                assertEquals(origEdge.label, rtEdge.label);
                assertEquals(origEdge.id, rtEdge.id);
                assertEquals(origEdge.out, rtEdge.out);
                assertEquals(origEdge.in, rtEdge.in);
                assertEquals(origEdge.properties.size(), rtEdge.properties.size());
                for (Map.Entry<PropertyKey, Literal> prop : origEdge.properties.entrySet()) {
                    assertEquals(prop.getValue(), rtEdge.properties.get(prop.getKey()),
                            "Property mismatch for " + prop.getKey().value);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Converts TinkerFactory.createModern() to a Hydra graph and spot-checks specific elements.
    @Test
    public void testTinkerFactoryModernToHydra() {
        try (TinkerGraph modern = TinkerFactory.createModern()) {
            Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(modern,
                    ExampleGraphs::objectToLiteral);

            assertEquals(6, hydraGraph.vertices.size());
            assertEquals(6, hydraGraph.edges.size());

            // Spot-check marko
            Literal markoId = hydra.dsl.Literals.int32(1);
            Vertex<Literal> marko = hydraGraph.vertices.get(markoId);
            assertNotNull(marko, "marko vertex not found");
            assertEquals("person", marko.label.value);
            assertEquals(hydra.dsl.Literals.string("marko"),
                    marko.properties.get(new PropertyKey("name")));
            assertEquals(hydra.dsl.Literals.int32(29),
                    marko.properties.get(new PropertyKey("age")));

            // Spot-check ripple
            Literal rippleId = hydra.dsl.Literals.int32(5);
            Vertex<Literal> ripple = hydraGraph.vertices.get(rippleId);
            assertNotNull(ripple, "ripple vertex not found");
            assertEquals("software", ripple.label.value);
            assertEquals(hydra.dsl.Literals.string("java"),
                    ripple.properties.get(new PropertyKey("lang")));

            // Spot-check knows edge (marko -> vadas)
            Literal knowsId = hydra.dsl.Literals.int32(7);
            Edge<Literal> knows = hydraGraph.edges.get(knowsId);
            assertNotNull(knows, "knows edge not found");
            assertEquals("knows", knows.label.value);
            assertEquals(markoId, knows.out);
            assertEquals(hydra.dsl.Literals.int32(2), knows.in);
            assertEquals(hydra.dsl.Literals.float64(0.5d),
                    knows.properties.get(new PropertyKey("weight")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Verifies that TinkerFactory.createModern() converted to Hydra equals the hand-built Hydra graph.
    @Test
    public void testTinkerFactoryModernEqualsHandBuilt() {
        Graph<Literal> handBuilt = ExampleGraphs.buildModernGraph();

        try (TinkerGraph modern = TinkerFactory.createModern()) {
            Graph<Literal> fromTinkerPop = HydraGremlinBridge.gremlinToHydra(modern,
                    ExampleGraphs::objectToLiteral);

            assertEquals(handBuilt, fromTinkerPop);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Verifies that adding an extra vertex makes the hand-built graph unequal to the converted TinkerPop graph.
    @Test
    public void testTinkerFactoryModernNotEqualsModified() {
        Graph<Literal> handBuilt = ExampleGraphs.buildModernGraph();

        // Add an extra vertex
        Vertex<Literal> zaphod = Graphs.<Literal>vertex("person", hydra.dsl.Literals.int32(99))
                .property("name", hydra.dsl.Literals.string("zaphod"))
                .property("age", hydra.dsl.Literals.int32(250))
                .build();
        Map<Literal, Vertex<Literal>> modifiedVertices = new HashMap<>(handBuilt.vertices);
        modifiedVertices.put(zaphod.id, zaphod);
        Graph<Literal> modified = new Graph<>(modifiedVertices, handBuilt.edges);

        try (TinkerGraph modern = TinkerFactory.createModern()) {
            Graph<Literal> fromTinkerPop = HydraGremlinBridge.gremlinToHydra(modern,
                    ExampleGraphs::objectToLiteral);

            assertNotEquals(modified, fromTinkerPop);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Full round-trip starting from TinkerFactory.createModern(): TinkerPop -> Hydra -> TinkerPop -> Hydra.
    @Test
    public void testTinkerFactoryModernRoundTrip() {
        try (TinkerGraph modern = TinkerFactory.createModern()) {
            // TinkerPop -> Hydra
            Graph<Literal> hydraGraph = HydraGremlinBridge.gremlinToHydra(modern,
                    ExampleGraphs::objectToLiteral);

            // Hydra -> fresh TinkerPop
            try (TinkerGraph rebuilt = TinkerGraph.open()) {
                HydraGremlinBridge.hydraToGremlin(hydraGraph, rebuilt,
                        ExampleGraphs::literalToObject);

                // Hydra -> TinkerPop -> Hydra again
                Graph<Literal> roundTripped = HydraGremlinBridge.gremlinToHydra(rebuilt,
                        ExampleGraphs::objectToLiteral);

                // Verify structural equality
                assertEquals(hydraGraph.vertices.size(), roundTripped.vertices.size());
                assertEquals(hydraGraph.edges.size(), roundTripped.edges.size());

                for (Map.Entry<Literal, Vertex<Literal>> entry : hydraGraph.vertices.entrySet()) {
                    Vertex<Literal> orig = entry.getValue();
                    Vertex<Literal> rt = roundTripped.vertices.get(entry.getKey());
                    assertNotNull(rt, "Missing vertex: " + entry.getKey());
                    assertEquals(orig.label, rt.label);
                    assertEquals(orig.id, rt.id);
                    for (Map.Entry<PropertyKey, Literal> prop : orig.properties.entrySet()) {
                        assertEquals(prop.getValue(), rt.properties.get(prop.getKey()),
                                "Property mismatch for " + prop.getKey().value);
                    }
                }

                for (Map.Entry<Literal, Edge<Literal>> entry : hydraGraph.edges.entrySet()) {
                    Edge<Literal> orig = entry.getValue();
                    Edge<Literal> rt = roundTripped.edges.get(entry.getKey());
                    assertNotNull(rt, "Missing edge: " + entry.getKey());
                    assertEquals(orig.label, rt.label);
                    assertEquals(orig.out, rt.out);
                    assertEquals(orig.in, rt.in);
                    for (Map.Entry<PropertyKey, Literal> prop : orig.properties.entrySet()) {
                        assertEquals(prop.getValue(), rt.properties.get(prop.getKey()),
                                "Property mismatch for " + prop.getKey().value);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
