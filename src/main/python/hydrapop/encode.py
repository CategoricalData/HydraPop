"""JSON encoders for Hydra property graph types.

Inverse of ``hydrapop.decode``: encodes ``hydra.pg.model`` objects into
the canonical Hydra term-JSON format. Maps are encoded as keyed lists
with ``key`` / ``value`` entries, matching the JSON produced by Java's
``hydra.encode.pg.model`` plus ``hydra.json.encode.Encode``.

Useful when authoring schemas or graphs in Python (rather than Java) and
emitting them as canonical JSON for downstream consumers.
"""

import hydra.core


# -- Helpers --

def _encode_map(mapping, encode_key, encode_value):
    """Encode a FrozenDict (or dict) as a list of key/value entries."""
    return [
        {"key": encode_key(k), "value": encode_value(v)}
        for k, v in mapping.items()
    ]


def _encode_list(seq, encode_fn):
    return [encode_fn(x) for x in seq]


# -- Type encoders --

def encode_literal_type(literal_type):
    if isinstance(literal_type, hydra.core.LiteralTypeBinary):
        return {"binary": {}}
    if isinstance(literal_type, hydra.core.LiteralTypeBoolean):
        return {"boolean": {}}
    if isinstance(literal_type, hydra.core.LiteralTypeString):
        return {"string": {}}
    if isinstance(literal_type, hydra.core.LiteralTypeFloat):
        return {"float": _encode_float_type(literal_type.value)}
    if isinstance(literal_type, hydra.core.LiteralTypeInteger):
        return {"integer": _encode_integer_type(literal_type.value)}
    raise ValueError(f"Unknown literal type: {literal_type}")


_FLOAT_TYPE_TAGS = {
    hydra.core.FloatType.FLOAT32: "float32",
    hydra.core.FloatType.FLOAT64: "float64",
}


def _encode_float_type(ft):
    try:
        return {_FLOAT_TYPE_TAGS[ft]: {}}
    except KeyError as e:
        raise ValueError(f"Unknown float type: {ft}") from e


_INTEGER_TYPE_TAGS = {
    hydra.core.IntegerType.BIGINT: "bigint",
    hydra.core.IntegerType.INT8: "int8",
    hydra.core.IntegerType.INT16: "int16",
    hydra.core.IntegerType.INT32: "int32",
    hydra.core.IntegerType.INT64: "int64",
    hydra.core.IntegerType.UINT8: "uint8",
    hydra.core.IntegerType.UINT16: "uint16",
    hydra.core.IntegerType.UINT32: "uint32",
    hydra.core.IntegerType.UINT64: "uint64",
}


def _encode_integer_type(it):
    try:
        return {_INTEGER_TYPE_TAGS[it]: {}}
    except KeyError as e:
        raise ValueError(f"Unknown integer type: {it}") from e


# -- Value encoders --

def encode_literal(literal):
    if isinstance(literal, hydra.core.LiteralBinary):
        return {"binary": literal.value.decode("utf-8")}
    if isinstance(literal, hydra.core.LiteralBoolean):
        return {"boolean": literal.value}
    if isinstance(literal, hydra.core.LiteralString):
        return {"string": literal.value}
    if isinstance(literal, hydra.core.LiteralFloat):
        return {"float": _encode_float_value(literal.value)}
    if isinstance(literal, hydra.core.LiteralInteger):
        return {"integer": _encode_integer_value(literal.value)}
    raise ValueError(f"Unknown literal: {literal}")


def _encode_float_value(fv):
    if isinstance(fv, hydra.core.FloatValueFloat32):
        return {"float32": fv.value}
    if isinstance(fv, hydra.core.FloatValueFloat64):
        return {"float64": fv.value}
    raise ValueError(f"Unknown float value: {fv}")


def _encode_integer_value(iv):
    if isinstance(iv, hydra.core.IntegerValueBigint):
        return {"bigint": iv.value}
    if isinstance(iv, hydra.core.IntegerValueInt8):
        return {"int8": iv.value}
    if isinstance(iv, hydra.core.IntegerValueInt16):
        return {"int16": iv.value}
    if isinstance(iv, hydra.core.IntegerValueInt32):
        return {"int32": iv.value}
    if isinstance(iv, hydra.core.IntegerValueInt64):
        return {"int64": iv.value}
    if isinstance(iv, hydra.core.IntegerValueUint8):
        return {"uint8": iv.value}
    if isinstance(iv, hydra.core.IntegerValueUint16):
        return {"uint16": iv.value}
    if isinstance(iv, hydra.core.IntegerValueUint32):
        return {"uint32": iv.value}
    if isinstance(iv, hydra.core.IntegerValueUint64):
        return {"uint64": iv.value}
    raise ValueError(f"Unknown integer value: {iv}")


# -- PG model encoders --

def encode_graph_schema(schema):
    return {
        "vertices": _encode_map(
            schema.vertices,
            lambda label: label.value,
            encode_vertex_type,
        ),
        "edges": _encode_map(
            schema.edges,
            lambda label: label.value,
            encode_edge_type,
        ),
    }


def encode_vertex_type(vt):
    return {
        "label": vt.label.value,
        "id": encode_literal_type(vt.id),
        "properties": _encode_list(vt.properties, encode_property_type),
    }


def encode_edge_type(et):
    return {
        "label": et.label.value,
        "id": encode_literal_type(et.id),
        "out": et.out.value,
        "in": et.in_.value,
        "properties": _encode_list(et.properties, encode_property_type),
    }


def encode_property_type(pt):
    return {
        "key": pt.key.value,
        "value": encode_literal_type(pt.value),
        "required": pt.required,
    }


def encode_graph(graph):
    return {
        "vertices": _encode_map(graph.vertices, encode_literal, encode_vertex),
        "edges": _encode_map(graph.edges, encode_literal, encode_edge),
    }


def encode_vertex(v):
    return {
        "label": v.label.value,
        "id": encode_literal(v.id),
        "properties": _encode_map(
            v.properties,
            lambda key: key.value,
            encode_literal,
        ),
    }


def encode_edge(e):
    return {
        "label": e.label.value,
        "id": encode_literal(e.id),
        "out": encode_literal(e.out),
        "in": encode_literal(e.in_),
        "properties": _encode_map(
            e.properties,
            lambda key: key.value,
            encode_literal,
        ),
    }
