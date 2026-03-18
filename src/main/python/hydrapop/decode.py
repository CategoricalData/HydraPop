"""JSON decoders for Hydra property graph types.

Decodes JSON produced by Java's Hydra term encoders into Python
hydra.pg.model objects. The JSON format matches the validatepg demo.
"""

import json
import os

import hydra.core
import hydra.pg.model as pg_model
from hydra.dsl.python import FrozenDict


# -- Helpers --

def _expect_obj(json_val):
    if not isinstance(json_val, dict):
        raise ValueError(f"Expected JSON object, got {type(json_val)}")
    return json_val


def _expect_list(json_val):
    if not isinstance(json_val, list):
        raise ValueError(f"Expected JSON array, got {type(json_val)}")
    return json_val


def _decode_map(json_val, decode_key, decode_value):
    entries = _expect_list(json_val)
    result = {}
    for entry in entries:
        obj = _expect_obj(entry)
        k = decode_key(obj["@key"])
        v = decode_value(obj["@value"])
        result[k] = v
    return FrozenDict(result)


def _decode_list(json_val, decode_fn):
    return tuple(decode_fn(x) for x in _expect_list(json_val))


# -- Type decoders --

def decode_literal_type(json_val):
    obj = _expect_obj(json_val)
    if "binary" in obj:
        return hydra.core.LiteralTypeBinary()
    if "boolean" in obj:
        return hydra.core.LiteralTypeBoolean()
    if "string" in obj:
        return hydra.core.LiteralTypeString()
    if "float" in obj:
        return hydra.core.LiteralTypeFloat(_decode_float_type(obj["float"]))
    if "integer" in obj:
        return hydra.core.LiteralTypeInteger(_decode_integer_type(obj["integer"]))
    raise ValueError(f"Unknown literal type: {obj}")


def _decode_float_type(json_val):
    obj = _expect_obj(json_val)
    if "bigfloat" in obj:
        return hydra.core.FloatType.BIGFLOAT
    if "float32" in obj:
        return hydra.core.FloatType.FLOAT32
    if "float64" in obj:
        return hydra.core.FloatType.FLOAT64
    raise ValueError(f"Unknown float type: {obj}")


def _decode_integer_type(json_val):
    obj = _expect_obj(json_val)
    if "bigint" in obj:
        return hydra.core.IntegerType.BIGINT
    if "int8" in obj:
        return hydra.core.IntegerType.INT8
    if "int16" in obj:
        return hydra.core.IntegerType.INT16
    if "int32" in obj:
        return hydra.core.IntegerType.INT32
    if "int64" in obj:
        return hydra.core.IntegerType.INT64
    if "uint8" in obj:
        return hydra.core.IntegerType.UINT8
    if "uint16" in obj:
        return hydra.core.IntegerType.UINT16
    if "uint32" in obj:
        return hydra.core.IntegerType.UINT32
    if "uint64" in obj:
        return hydra.core.IntegerType.UINT64
    raise ValueError(f"Unknown integer type: {obj}")


# -- Value decoders --

def decode_literal(json_val):
    obj = _expect_obj(json_val)
    if "binary" in obj:
        return hydra.core.LiteralBinary(obj["binary"].encode("utf-8"))
    if "boolean" in obj:
        return hydra.core.LiteralBoolean(obj["boolean"])
    if "string" in obj:
        return hydra.core.LiteralString(obj["string"])
    if "float" in obj:
        return hydra.core.LiteralFloat(_decode_float_value(obj["float"]))
    if "integer" in obj:
        return hydra.core.LiteralInteger(_decode_integer_value(obj["integer"]))
    raise ValueError(f"Unknown literal: {obj}")


def _decode_float_value(json_val):
    from decimal import Decimal

    obj = _expect_obj(json_val)
    if "bigfloat" in obj:
        return hydra.core.FloatValueBigfloat(Decimal(str(obj["bigfloat"])))
    if "float32" in obj:
        return hydra.core.FloatValueFloat32(float(obj["float32"]))
    if "float64" in obj:
        return hydra.core.FloatValueFloat64(float(obj["float64"]))
    raise ValueError(f"Unknown float value: {obj}")


def _decode_integer_value(json_val):
    obj = _expect_obj(json_val)
    if "bigint" in obj:
        return hydra.core.IntegerValueBigint(int(obj["bigint"]))
    if "int8" in obj:
        return hydra.core.IntegerValueInt8(int(obj["int8"]))
    if "int16" in obj:
        return hydra.core.IntegerValueInt16(int(obj["int16"]))
    if "int32" in obj:
        return hydra.core.IntegerValueInt32(int(obj["int32"]))
    if "int64" in obj:
        return hydra.core.IntegerValueInt64(int(obj["int64"]))
    if "uint8" in obj:
        return hydra.core.IntegerValueUint8(int(obj["uint8"]))
    if "uint16" in obj:
        return hydra.core.IntegerValueUint16(int(obj["uint16"]))
    if "uint32" in obj:
        return hydra.core.IntegerValueUint32(int(obj["uint32"]))
    if "uint64" in obj:
        return hydra.core.IntegerValueUint64(int(obj["uint64"]))
    raise ValueError(f"Unknown integer value: {obj}")


# -- PG model decoders --

def decode_graph_schema(json_val):
    obj = _expect_obj(json_val)
    return pg_model.GraphSchema(
        vertices=_decode_map(
            obj["vertices"],
            lambda v: pg_model.VertexLabel(v),
            decode_vertex_type,
        ),
        edges=_decode_map(
            obj["edges"],
            lambda v: pg_model.EdgeLabel(v),
            decode_edge_type,
        ),
    )


def decode_vertex_type(json_val):
    obj = _expect_obj(json_val)
    return pg_model.VertexType(
        label=pg_model.VertexLabel(obj["label"]),
        id=decode_literal_type(obj["id"]),
        properties=_decode_list(obj["properties"], decode_property_type),
    )


def decode_edge_type(json_val):
    obj = _expect_obj(json_val)
    return pg_model.EdgeType(
        label=pg_model.EdgeLabel(obj["label"]),
        id=decode_literal_type(obj["id"]),
        out=pg_model.VertexLabel(obj["out"]),
        in_=pg_model.VertexLabel(obj["in"]),
        properties=_decode_list(obj["properties"], decode_property_type),
    )


def decode_property_type(json_val):
    obj = _expect_obj(json_val)
    return pg_model.PropertyType(
        key=pg_model.PropertyKey(obj["key"]),
        value=decode_literal_type(obj["value"]),
        required=obj["required"],
    )


def decode_graph(json_val):
    obj = _expect_obj(json_val)
    return pg_model.Graph(
        vertices=_decode_map(obj["vertices"], decode_literal, decode_vertex),
        edges=_decode_map(obj["edges"], decode_literal, decode_edge),
    )


def decode_vertex(json_val):
    obj = _expect_obj(json_val)
    return pg_model.Vertex(
        label=pg_model.VertexLabel(obj["label"]),
        id=decode_literal(obj["id"]),
        properties=_decode_map(
            obj["properties"],
            lambda v: pg_model.PropertyKey(v),
            decode_literal,
        ),
    )


def decode_edge(json_val):
    obj = _expect_obj(json_val)
    return pg_model.Edge(
        label=pg_model.EdgeLabel(obj["label"]),
        id=decode_literal(obj["id"]),
        out=decode_literal(obj["out"]),
        in_=decode_literal(obj["in"]),
        properties=_decode_map(
            obj["properties"],
            lambda v: pg_model.PropertyKey(v),
            decode_literal,
        ),
    )


# -- File loading --

def _data_dir():
    """Return the path to the generated JSON data directory."""
    # __file__ is src/main/python/hydrapop/decode.py
    # project root is 5 levels up (decode.py → hydrapop → python → main → src → root)
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))))))
    return os.path.join(project_root, "src", "gen-main", "json")


def load_schema():
    with open(os.path.join(_data_dir(), "schema.json")) as f:
        return decode_graph_schema(json.load(f))


def load_graph(name):
    with open(os.path.join(_data_dir(), name + ".json")) as f:
        return decode_graph(json.load(f))
