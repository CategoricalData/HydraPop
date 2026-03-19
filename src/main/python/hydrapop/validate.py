"""Validation helpers for property graphs using Hydra.

Provides the checkValue and showValue callbacks required by
hydra.pg.validation.validate_graph.
"""

import hydra.core
import hydra.lib.maybes
from hydra.dsl.python import Just, Nothing

# Patch hydra.lib.maybes.maybe to support lazy default arguments (lambdas).
# The hydra.pg.validation module (generated from Hydra 0.14.0+) passes lambdas
# as the default argument, but hydra-python 0.13.0's maybe() treats them as
# plain values. This can be removed once hydra-python >= 0.14.0 is available.
_original_maybe = hydra.lib.maybes.maybe


def _lazy_maybe(default, f, x):
    result = _original_maybe(default, f, x)
    return result() if callable(result) else result


hydra.lib.maybes.maybe = _lazy_maybe


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


class Result:
    """The result of validating a graph against a schema."""

    def __init__(self, error):
        self._error = error

    @property
    def is_valid(self):
        return self._error is None

    @property
    def error(self):
        return self._error

    def __repr__(self):
        return "VALID" if self._error is None else f"INVALID - {self._error}"


def validate(schema, g):
    """Validate a TinkerPop graph (via gremlinpython traversal source) against a schema.

    Parameters
    ----------
    schema : hydra.pg.model.GraphSchema
        The schema to validate against.
    g : GraphTraversalSource
        A gremlinpython traversal source connected to a Gremlin Server.

    Returns
    -------
    Result
        A result whose ``repr()`` is either "VALID" or "INVALID - ...".
    """
    import hydra.pg.validation as pg_validation
    from hydrapop.gremlin_bridge import gremlin_to_hydra

    hydra_graph = gremlin_to_hydra(g)
    result = pg_validation.validate_graph(check_literal, show_literal, schema, hydra_graph)
    match result:
        case Just(msg):
            return Result(msg)
        case _:
            return Result(None)
