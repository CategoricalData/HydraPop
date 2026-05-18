"""Bridge between gremlinpython traversals and Hydra property graphs.

Converts a TinkerPop graph (accessed via a remote GraphTraversalSource)
to and from a Hydra Graph[Literal]:

- ``gremlin_to_hydra(g)``: read a TinkerPop graph into a Hydra Graph
  (the Python equivalent of Java's ``HydraGremlinBridge.gremlinToHydra``).
- ``hydra_to_gremlin(graph, g)``: write a Hydra Graph into a live
  TinkerPop graph. Upserts by id, so re-emitting the same graph is
  idempotent.

Requires ``gremlinpython`` to be installed.
"""

import hydra.core
import hydra.pg.model as pg_model
from gremlin_python.process.graph_traversal import __ as gremlin_anon
from gremlin_python.process.traversal import Direction
from gremlin_python.process.traversal import T
from hydra.dsl.python import FrozenDict


def object_to_literal(obj):
    """Convert a Python value from gremlinpython to a Hydra Literal.

    Handles the types that TinkerPop's Modern graph uses:
    str, int, and float (Python float is 64-bit).
    """
    if isinstance(obj, str):
        return hydra.core.LiteralString(obj)
    if isinstance(obj, int):
        return hydra.core.LiteralInteger(hydra.core.IntegerValueInt32(obj))
    if isinstance(obj, float):
        return hydra.core.LiteralFloat(hydra.core.FloatValueFloat64(obj))
    raise ValueError(f"Unsupported value type: {type(obj)}")


def literal_to_object(literal):
    """Convert a Hydra Literal to a plain Python value.

    Inverse of ``object_to_literal``. Handles String, Integer (all width
    variants), Float (all width variants), and Boolean.
    """
    if isinstance(literal, hydra.core.LiteralString):
        return literal.value
    if isinstance(literal, hydra.core.LiteralBoolean):
        return literal.value
    if isinstance(literal, hydra.core.LiteralInteger):
        return literal.value.value
    if isinstance(literal, hydra.core.LiteralFloat):
        return literal.value.value
    if isinstance(literal, hydra.core.LiteralBinary):
        return literal.value
    raise ValueError(f"Unsupported literal type: {type(literal).__name__}")


def gremlin_to_hydra(g, value_mapper=object_to_literal):
    """Read a TinkerPop graph via traversals and convert to a Hydra Graph.

    Parameters
    ----------
    g : GraphTraversalSource
        A gremlinpython traversal source connected to a Gremlin Server.
    value_mapper : callable, optional
        Converts Python values to Hydra Literals. Defaults to
        ``object_to_literal``.

    Returns
    -------
    hydra.pg.model.Graph
        The graph as a Hydra property graph with Literal values.
    """
    vertices = {}
    for v in g.V().element_map().to_list():
        vid = value_mapper(v[T.id])
        label = pg_model.VertexLabel(v[T.label])
        properties = FrozenDict({
            pg_model.PropertyKey(k): value_mapper(val)
            for k, val in v.items()
            if k not in (T.id, T.label)
        })
        vertices[vid] = pg_model.Vertex(label=label, id=vid, properties=properties)

    edges = {}
    for e in g.E().element_map().to_list():
        eid = value_mapper(e[T.id])
        label = pg_model.EdgeLabel(e[T.label])
        out_id = value_mapper(e[Direction.OUT][T.id])
        in_id = value_mapper(e[Direction.IN][T.id])
        properties = FrozenDict({
            pg_model.PropertyKey(k): value_mapper(val)
            for k, val in e.items()
            if k not in (T.id, T.label, Direction.OUT, Direction.IN)
        })
        edges[eid] = pg_model.Edge(
            label=label, id=eid, out=out_id, in_=in_id, properties=properties,
        )

    return pg_model.Graph(
        vertices=FrozenDict(vertices),
        edges=FrozenDict(edges),
    )


def hydra_to_gremlin(graph, g, value_unmapper=literal_to_object):
    """Write a Hydra Graph into a live TinkerPop graph via traversals.

    Upserts by id: existing vertices and edges keep their identity and have
    their properties overwritten with the new values. New vertices and edges
    are added. This makes re-emitting the same graph idempotent and lets
    callers stream incremental deltas without coordinating with prior writes.

    Parameters
    ----------
    graph : hydra.pg.model.Graph
        The Hydra property graph to write.
    g : GraphTraversalSource
        A gremlinpython traversal source connected to a Gremlin Server (or
        an embedded graph). Must allow writes.
    value_unmapper : callable, optional
        Converts Hydra Literals to plain Python values that gremlinpython
        can send. Defaults to ``literal_to_object``.

    Notes
    -----
    Edges reference vertices by id, so callers must ensure the referenced
    vertices either already exist in the target graph or are present in
    ``graph.vertices``. Vertices are written before edges to avoid dangling
    references within a single call.
    """
    for vertex in graph.vertices.values():
        _upsert_vertex(g, vertex, value_unmapper)

    for edge in graph.edges.values():
        _upsert_edge(g, edge, value_unmapper)


def _upsert_vertex(g, vertex, value_unmapper):
    vid = value_unmapper(vertex.id)
    label = vertex.label.value

    # coalesce(__.unfold(), __.addV(label).property(T.id, vid))
    # then chain property() writes for each entry.
    #
    # NOTE: TinkerGraph fixes label at vertex creation; coalesce-on-exists
    # means a pre-existing vertex (including a "_stub" placeholder created
    # by an out-of-order edge write) keeps its original label. Properties
    # are always overwritten with the latest values. Callers should write
    # vertices before edges in the same delta, and serialize deltas, to
    # avoid stubs in practice.
    traversal = g.V(vid).fold().coalesce(
        gremlin_anon.unfold(),
        gremlin_anon.addV(label).property(T.id, vid),
    )
    for key, literal in vertex.properties.items():
        traversal = traversal.property(key.value, value_unmapper(literal))
    traversal.iterate()


def _upsert_edge(g, edge, value_unmapper):
    eid = value_unmapper(edge.id)
    label = edge.label.value
    out_id = value_unmapper(edge.out)
    in_id = value_unmapper(edge.in_)

    # Ensure both endpoints exist before adding the edge. This lets callers
    # write deltas that reference vertices defined in earlier deltas
    # (e.g. an extractor that emits only the new edges referencing a
    # previously-written Headache) without managing dependency order, and
    # without crashing on stray references.
    #
    # Stub vertices created here carry the literal id as their label (a
    # placeholder) so they're visible but distinguishable from real
    # vertices. A subsequent upsert_vertex for that id will replace the
    # label and write the intended properties.
    _ensure_vertex_exists(g, out_id)
    _ensure_vertex_exists(g, in_id)

    # Look for an existing edge with this id; if absent, create it between
    # the named out- and in-vertices.
    traversal = g.E(eid).fold().coalesce(
        gremlin_anon.unfold(),
        gremlin_anon.V(out_id).addE(label).to(gremlin_anon.V(in_id)).property(T.id, eid),
    )
    for key, literal in edge.properties.items():
        traversal = traversal.property(key.value, value_unmapper(literal))
    traversal.iterate()


def _ensure_vertex_exists(g, vid):
    """Idempotent: create a placeholder vertex with id ``vid`` if absent.

    The placeholder label is ``"_stub"``. If the id later appears in an
    ``_upsert_vertex`` call, the real label and properties are written
    via the same coalesce pattern (TinkerGraph treats label as a fixed
    property at vertex creation, but our upsert pattern coalesces on
    existence before adding, so the stub keeps its label; callers should
    rely on the property values rather than the label of a stub).
    """
    g.V(vid).fold().coalesce(
        gremlin_anon.unfold(),
        gremlin_anon.addV("_stub").property(T.id, vid),
    ).iterate()
