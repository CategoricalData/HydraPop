package net.fortytwo.hydra.hydrapop;

import hydra.pg.model.Edge;
import hydra.pg.model.EdgeLabel;
import hydra.pg.model.Graph;
import hydra.pg.model.PropertyKey;
import hydra.pg.model.Vertex;
import hydra.pg.model.VertexLabel;

import org.apache.tinkerpop.gremlin.structure.T;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

/**
 * Bidirectional bridge between Hydra property graphs and TinkerPop (Gremlin) graphs.
 */
public class HydraGremlinBridge {

    private HydraGremlinBridge() {
    }

    /**
     * Populates a TinkerPop graph from a Hydra property graph.
     */
    public static <V> void hydraToGremlin(
            Graph<V> hydraGraph,
            org.apache.tinkerpop.gremlin.structure.Graph gremlinGraph,
            Function<V, Object> valueToObject) {

        for (Map.Entry<V, Vertex<V>> entry : hydraGraph.vertices.entrySet()) {
            Vertex<V> hv = entry.getValue();
            Object id = valueToObject.apply(hv.id);
            String label = hv.label.value;

            Object[] keyValues = new Object[4 + hv.properties.size() * 2];
            keyValues[0] = T.id;
            keyValues[1] = id;
            keyValues[2] = T.label;
            keyValues[3] = label;
            int i = 4;
            for (Map.Entry<PropertyKey, V> prop : hv.properties.entrySet()) {
                keyValues[i++] = prop.getKey().value;
                keyValues[i++] = valueToObject.apply(prop.getValue());
            }

            gremlinGraph.addVertex(keyValues);
        }

        for (Map.Entry<V, Edge<V>> entry : hydraGraph.edges.entrySet()) {
            Edge<V> he = entry.getValue();
            Object id = valueToObject.apply(he.id);
            Object outId = valueToObject.apply(he.out);
            Object inId = valueToObject.apply(he.in);
            String label = he.label.value;

            org.apache.tinkerpop.gremlin.structure.Vertex outVertex = findVertex(gremlinGraph, outId);
            org.apache.tinkerpop.gremlin.structure.Vertex inVertex = findVertex(gremlinGraph, inId);

            Object[] keyValues = new Object[2 + he.properties.size() * 2];
            keyValues[0] = T.id;
            keyValues[1] = id;
            int i = 2;
            for (Map.Entry<PropertyKey, V> prop : he.properties.entrySet()) {
                keyValues[i++] = prop.getKey().value;
                keyValues[i++] = valueToObject.apply(prop.getValue());
            }

            outVertex.addEdge(label, inVertex, keyValues);
        }
    }

    /**
     * Reads a TinkerPop graph into a Hydra property graph.
     */
    public static <V> Graph<V> gremlinToHydra(
            org.apache.tinkerpop.gremlin.structure.Graph gremlinGraph,
            Function<Object, V> objectToValue) {

        Map<V, Vertex<V>> vertices = new HashMap<>();
        Iterator<org.apache.tinkerpop.gremlin.structure.Vertex> vertexIter = gremlinGraph.vertices();
        while (vertexIter.hasNext()) {
            org.apache.tinkerpop.gremlin.structure.Vertex gv = vertexIter.next();
            V id = objectToValue.apply(gv.id());
            VertexLabel label = new VertexLabel(gv.label());

            Map<PropertyKey, V> properties = new HashMap<>();
            Iterator<org.apache.tinkerpop.gremlin.structure.VertexProperty<Object>> propIter = gv.properties();
            while (propIter.hasNext()) {
                org.apache.tinkerpop.gremlin.structure.VertexProperty<Object> prop = propIter.next();
                properties.put(new PropertyKey(prop.key()), objectToValue.apply(prop.value()));
            }

            vertices.put(id, new Vertex<>(label, id, properties));
        }

        Map<V, Edge<V>> edges = new HashMap<>();
        Iterator<org.apache.tinkerpop.gremlin.structure.Edge> edgeIter = gremlinGraph.edges();
        while (edgeIter.hasNext()) {
            org.apache.tinkerpop.gremlin.structure.Edge ge = edgeIter.next();
            V id = objectToValue.apply(ge.id());
            EdgeLabel label = new EdgeLabel(ge.label());
            V outId = objectToValue.apply(ge.outVertex().id());
            V inId = objectToValue.apply(ge.inVertex().id());

            Map<PropertyKey, V> properties = new HashMap<>();
            Iterator<org.apache.tinkerpop.gremlin.structure.Property<Object>> propIter = ge.properties();
            while (propIter.hasNext()) {
                org.apache.tinkerpop.gremlin.structure.Property<Object> prop = propIter.next();
                properties.put(new PropertyKey(prop.key()), objectToValue.apply(prop.value()));
            }

            edges.put(id, new Edge<>(label, id, outId, inId, properties));
        }

        return new Graph<>(vertices, edges);
    }

    private static org.apache.tinkerpop.gremlin.structure.Vertex findVertex(
            org.apache.tinkerpop.gremlin.structure.Graph graph, Object id) {
        Iterator<org.apache.tinkerpop.gremlin.structure.Vertex> iter = graph.vertices(id);
        if (!iter.hasNext()) {
            throw new IllegalArgumentException("Vertex not found: " + id);
        }
        return iter.next();
    }
}
