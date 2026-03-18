"""Validation helpers for property graphs using Hydra.

Provides the checkValue and showValue callbacks required by
hydra.pg.validation.validate_graph.
"""

import hydra.core
from hydra.dsl.python import Just, Nothing


_FLOAT_TYPE_NAMES = {
    hydra.core.FloatType.BIGFLOAT: "bigfloat",
    hydra.core.FloatType.FLOAT32: "float32",
    hydra.core.FloatType.FLOAT64: "float64",
}

_INTEGER_TYPE_NAMES = {
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

_FLOAT_VALUE_FAMILY = {
    hydra.core.FloatValueBigfloat: "float:bigfloat",
    hydra.core.FloatValueFloat32: "float:float32",
    hydra.core.FloatValueFloat64: "float:float64",
}

_INTEGER_VALUE_FAMILY = {
    hydra.core.IntegerValueBigint: "integer:bigint",
    hydra.core.IntegerValueInt8: "integer:int8",
    hydra.core.IntegerValueInt16: "integer:int16",
    hydra.core.IntegerValueInt32: "integer:int32",
    hydra.core.IntegerValueInt64: "integer:int64",
    hydra.core.IntegerValueUint8: "integer:uint8",
    hydra.core.IntegerValueUint16: "integer:uint16",
    hydra.core.IntegerValueUint32: "integer:uint32",
    hydra.core.IntegerValueUint64: "integer:uint64",
}


def show_literal_type(lt):
    if isinstance(lt, hydra.core.LiteralTypeBinary):
        return "binary"
    if isinstance(lt, hydra.core.LiteralTypeBoolean):
        return "boolean"
    if isinstance(lt, hydra.core.LiteralTypeString):
        return "string"
    if isinstance(lt, hydra.core.LiteralTypeFloat):
        return "float:" + _FLOAT_TYPE_NAMES[lt.value]
    if isinstance(lt, hydra.core.LiteralTypeInteger):
        return "integer:" + _INTEGER_TYPE_NAMES[lt.value]
    raise ValueError(f"Unknown literal type: {lt}")


def literal_family(lit):
    if isinstance(lit, hydra.core.LiteralBinary):
        return "binary"
    if isinstance(lit, hydra.core.LiteralBoolean):
        return "boolean"
    if isinstance(lit, hydra.core.LiteralString):
        return "string"
    if isinstance(lit, hydra.core.LiteralFloat):
        return _FLOAT_VALUE_FAMILY.get(type(lit.value), "float:unknown")
    if isinstance(lit, hydra.core.LiteralInteger):
        return _INTEGER_VALUE_FAMILY.get(type(lit.value), "integer:unknown")
    raise ValueError(f"Unknown literal: {lit}")


def show_literal(lit):
    if isinstance(lit, hydra.core.LiteralBinary):
        return "binary:..."
    if isinstance(lit, hydra.core.LiteralBoolean):
        return f"boolean:{lit.value}"
    if isinstance(lit, hydra.core.LiteralString):
        return f'string:"{lit.value}"'
    if isinstance(lit, hydra.core.LiteralFloat):
        fv = lit.value
        family = _FLOAT_VALUE_FAMILY.get(type(fv), "float:unknown")
        return f"{family}:{fv.value}"
    if isinstance(lit, hydra.core.LiteralInteger):
        iv = lit.value
        family = _INTEGER_VALUE_FAMILY.get(type(iv), "integer:unknown")
        return f"{family}:{iv.value}"
    raise ValueError(f"Unknown literal: {lit}")


def check_literal(lt, lv):
    expected = show_literal_type(lt)
    actual = literal_family(lv)
    if expected == actual:
        return Nothing()
    return Just(f"expected {expected}, got {actual}")
