"""Validation helpers for property graphs using Hydra.

Provides the ``check_value`` callback required by ``hydra.validate.pg.validate_graph``,
plus a high-level ``validate(schema, g)`` convenience wrapper.

Hydra 0.15+ uses typed errors (``hydra.error.pg.InvalidGraphError``) rather than
the bare ``Maybe[str]`` result of 0.14. As of 0.16, ``validate_graph`` takes a
``ValidationProfile`` and a ``ValidationResult`` accumulator and returns a
``ValidationResult`` collecting every finding; this wrapper collapses that to
first-error semantics. The ``Result`` class preserves the typed value for
programmatic inspection while still providing a printable form.
"""

import hydra.core
import hydra.error.pg
from hydra.dsl.python import Given, None_


_FLOAT_TYPE_NAMES = {
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
    """Check that a Literal value matches an expected LiteralType.

    Returns ``None_()`` if the types match, ``Given(InvalidValueError(...))``
    otherwise. This is the ``check_value`` callback for ``hydra.validate.pg.validate_graph``.
    """
    expected = show_literal_type(lt)
    actual = literal_family(lv)
    if expected == actual:
        return None_()
    return Given(hydra.error.pg.InvalidValueError(expected, show_literal(lv)))


class Result:
    """The result of validating a graph against a schema.

    In 0.15+, ``error`` carries the typed ``InvalidGraphError`` rather than a
    bare string; callers wanting a human-readable rendering can use ``repr(result)``.
    """

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
        A result whose ``repr()`` is either "VALID" or "INVALID - ...". The
        ``error`` attribute, when present, is a typed ``InvalidGraphError``.
    """
    import hydra.validate.pg as pg_validation
    import hydra.validation
    from hydrapop.gremlin_bridge import gremlin_to_hydra

    hydra_graph = gremlin_to_hydra(g)
    # 0.16+: validate_graph takes a ValidationProfile and a ValidationResult
    # accumulator, returning a ValidationResult whose ``errors`` collects every
    # finding. Collapse to first-error semantics to match Result.
    result = pg_validation.validate_graph(
        pg_validation.default_pg_profile(),
        hydra.validation.ValidationResult(errors=[], warnings=[]),
        check_literal,
        schema,
        hydra_graph,
    )
    return Result(result.errors[0] if result.errors else None)
