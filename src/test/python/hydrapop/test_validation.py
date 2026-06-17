"""Validation tests for TinkerPop Modern graph using Hydra.

These tests mirror the Java TinkerGraphValidationTest. The schema and graph
data are defined in Java (the source of truth), encoded as JSON via Hydra's
term encoders, and decoded here for validation using hydra.validate.pg.

Hydra 0.15+ returns typed ``InvalidGraphError`` values rather than ``Maybe[str]``;
the assertions below pattern-match on the typed variants.
"""

import hydra.error.pg as err
import hydra.validate.pg as pg_validation
import hydra.validation
from hydra.dsl.python import Given, None_

from hydrapop.decode import load_graph, load_schema
from hydrapop.validate import check_literal


schema = load_schema()


def validate(graph):
    # 0.16+: validate_graph takes a ValidationProfile and a ValidationResult
    # accumulator. Collapse the resulting errors list to first-error Given/None_
    # so the assertion helpers below stay unchanged.
    result = pg_validation.validate_graph(
        pg_validation.default_pg_profile(),
        hydra.validation.ValidationResult(errors=[], warnings=[]),
        check_literal,
        schema,
        graph,
    )
    return Given(result.errors[0]) if result.errors else None_()


def assert_valid(result):
    match result:
        case None_():
            pass
        case Given(e):
            raise AssertionError(f"Expected valid graph but got: {e}")


def assert_invalid(result, predicate, description):
    match result:
        case None_():
            raise AssertionError("Expected validation error but graph was valid")
        case Given(e):
            assert predicate(e), (
                f"Validation error did not match {description}; got: {e}"
            )


# -- Predicate combinators on the typed PG error sums ----------------------


def is_vertex_error(inner):
    return lambda e: isinstance(e, err.InvalidGraphErrorVertex) and inner(e.value.error)


def is_edge_error(inner):
    return lambda e: isinstance(e, err.InvalidGraphErrorEdge) and inner(e.value.error)


def is_vertex_id_error():
    return is_vertex_error(lambda v: isinstance(v, err.InvalidVertexErrorId))


def is_vertex_label_error():
    return is_vertex_error(lambda v: isinstance(v, err.InvalidVertexErrorLabel))


def is_vertex_property_error(inner):
    return is_vertex_error(
        lambda v: isinstance(v, err.InvalidVertexErrorProperty) and inner(v.value.error)
    )


def is_edge_label_error():
    return is_edge_error(lambda e: isinstance(e, err.InvalidEdgeErrorLabel))


def is_edge_property_error(inner):
    return is_edge_error(
        lambda e: isinstance(e, err.InvalidEdgeErrorProperty) and inner(e.value.error)
    )


def is_wrong_in_vertex_label():
    return lambda e: isinstance(e, err.InvalidEdgeErrorInVertexLabel)


def is_wrong_out_vertex_label():
    return lambda e: isinstance(e, err.InvalidEdgeErrorOutVertexLabel)


def is_vertex_not_found():
    return lambda e: isinstance(
        e, (err.InvalidEdgeErrorInVertexNotFound, err.InvalidEdgeErrorOutVertexNotFound)
    )


def is_invalid_value():
    return lambda p: isinstance(p, err.InvalidPropertyErrorInvalidValue)


def is_missing_required():
    return lambda p: isinstance(p, err.InvalidPropertyErrorMissingRequired)


def is_unexpected_key():
    return lambda p: isinstance(p, err.InvalidPropertyErrorUnexpectedKey)


# -- Tests ------------------------------------------------------------------


def test_valid_graph():
    graph = load_graph("modern_graph")
    assert_valid(validate(graph))


def test_missing_required_property():
    graph = load_graph("missing_required_property")
    assert_invalid(
        validate(graph),
        is_vertex_property_error(is_missing_required()),
        "vertex property: missing required",
    )


def test_wrong_id_type():
    graph = load_graph("wrong_id_type")
    assert_invalid(validate(graph), is_vertex_id_error(), "vertex id error")


def test_unexpected_vertex_label():
    graph = load_graph("unexpected_vertex_label")
    assert_invalid(validate(graph), is_vertex_label_error(), "unknown vertex label")


def test_unexpected_edge_label():
    graph = load_graph("unexpected_edge_label")
    assert_invalid(validate(graph), is_edge_label_error(), "unknown edge label")


def test_property_value_type_mismatch():
    graph = load_graph("property_value_type_mismatch")
    assert_invalid(
        validate(graph),
        is_vertex_property_error(is_invalid_value()),
        "vertex property: invalid value",
    )


def test_unexpected_property_key():
    graph = load_graph("unexpected_property_key")
    assert_invalid(
        validate(graph),
        is_vertex_property_error(is_unexpected_key()),
        "vertex property: unexpected key",
    )


def test_wrong_in_vertex_label():
    graph = load_graph("wrong_in_vertex_label")
    assert_invalid(
        validate(graph),
        is_edge_error(is_wrong_in_vertex_label()),
        "edge in-vertex wrong label",
    )


def test_wrong_out_vertex_label():
    graph = load_graph("wrong_out_vertex_label")
    assert_invalid(
        validate(graph),
        is_edge_error(is_wrong_out_vertex_label()),
        "edge out-vertex wrong label",
    )


def test_missing_required_edge_property():
    graph = load_graph("missing_required_edge_property")
    assert_invalid(
        validate(graph),
        is_edge_property_error(is_missing_required()),
        "edge property: missing required",
    )


def test_unknown_edge_endpoint():
    graph = load_graph("unknown_edge_endpoint")
    assert_invalid(
        validate(graph),
        is_edge_error(is_vertex_not_found()),
        "edge references non-existent vertex",
    )
