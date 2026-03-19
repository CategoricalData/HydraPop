"""Bridge between gremlinpython traversals and Hydra property graphs.

Converts a TinkerPop graph (accessed via a remote GraphTraversalSource)
into a Hydra Graph[Literal] for validation. This is the Python equivalent
of Java's HydraGremlinBridge.gremlinToHydra().

Requires ``gremlinpython`` to be installed.
"""

import hydra.core
import hydra.pg.model as pg_model
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
