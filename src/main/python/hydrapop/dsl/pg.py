"""Python DSL for building Hydra property graph schemas.

Mirrors the Java ``hydra.pg.dsl.Graphs`` builder API, adapted for Python.
"""

import hydra.core as core
import hydra.pg.model as pg
from hydra.dsl.python import FrozenDict


# -- Literal type shortcuts --

def int32():
    """Int32 literal type."""
    return core.LiteralTypeInteger(core.IntegerType.INT32)


def string():
    """String literal type."""
    return core.LiteralTypeString()


def float64():
    """Float64 literal type."""
    return core.LiteralTypeFloat(core.FloatType.FLOAT64)


# -- Schema builders --

def vertex_type(label, id_type):
    """Start building a VertexType.

    Parameters
    ----------
    label : str
        The vertex label.
    id_type : LiteralType
        The type of the vertex id.

    Returns
    -------
    _VertexTypeBuilder
    """
    return _VertexTypeBuilder(label, id_type)


def edge_type(label, id_type, out_label, in_label):
    """Start building an EdgeType.

    Parameters
    ----------
    label : str
        The edge label.
    id_type : LiteralType
        The type of the edge id.
    out_label : str
        The label of the out-vertex.
    in_label : str
        The label of the in-vertex.

    Returns
    -------
    _EdgeTypeBuilder
    """
    return _EdgeTypeBuilder(label, id_type, out_label, in_label)


def graph_schema(vertex_types, edge_types):
    """Build a GraphSchema from lists of VertexType and EdgeType.

    Parameters
    ----------
    vertex_types : list of VertexType
        The vertex types.
    edge_types : list of EdgeType
        The edge types.

    Returns
    -------
    hydra.pg.model.GraphSchema
    """
    return pg.GraphSchema(
        vertices=FrozenDict({vt.label: vt for vt in vertex_types}),
        edges=FrozenDict({et.label: et for et in edge_types}),
    )


class _VertexTypeBuilder:
    def __init__(self, label, id_type):
        self._label = label
        self._id_type = id_type
        self._properties = []

    def property(self, key, value_type, required):
        """Add a property to the vertex type."""
        self._properties.append(
            pg.PropertyType(key=pg.PropertyKey(key), value=value_type, required=required))
        return self

    def build(self):
        """Build the VertexType."""
        return pg.VertexType(
            label=pg.VertexLabel(self._label),
            id=self._id_type,
            properties=tuple(self._properties),
        )


class _EdgeTypeBuilder:
    def __init__(self, label, id_type, out_label, in_label):
        self._label = label
        self._id_type = id_type
        self._out_label = out_label
        self._in_label = in_label
        self._properties = []

    def property(self, key, value_type, required):
        """Add a property to the edge type."""
        self._properties.append(
            pg.PropertyType(key=pg.PropertyKey(key), value=value_type, required=required))
        return self

    def build(self):
        """Build the EdgeType."""
        return pg.EdgeType(
            label=pg.EdgeLabel(self._label),
            id=self._id_type,
            out=pg.VertexLabel(self._out_label),
            in_=pg.VertexLabel(self._in_label),
            properties=tuple(self._properties),
        )
