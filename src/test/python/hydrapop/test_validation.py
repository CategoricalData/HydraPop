"""Validation tests for TinkerPop Modern graph using Hydra.

These tests mirror the Java TinkerGraphValidationTest. The schema and graph
data are defined in Java (the source of truth), encoded as JSON via Hydra's
term encoders, and decoded here for validation using hydra.pg.validation.
"""

import hydra.pg.validation as pg_validation
from hydra.dsl.python import Just, Nothing

from hydrapop.decode import load_graph, load_schema
from hydrapop.validate import check_literal, show_literal


schema = load_schema()


def validate(graph):
    return pg_validation.validate_graph(check_literal, show_literal, schema, graph)


def assert_valid(result):
    match result:
        case Nothing():
            pass
        case Just(msg):
            raise AssertionError(f"Expected valid graph but got: {msg}")


def assert_invalid(result, expected_substring):
    match result:
        case Nothing():
            raise AssertionError("Expected validation error but graph was valid")
        case Just(msg):
            assert expected_substring in msg, (
                f'Expected error containing "{expected_substring}" but got: {msg}'
            )


def test_valid_graph():
    graph = load_graph("modern_graph")
    assert_valid(validate(graph))


def test_missing_required_property():
    graph = load_graph("missing_required_property")
    assert_invalid(validate(graph), "Missing value for")


def test_wrong_id_type():
    graph = load_graph("wrong_id_type")
    assert_invalid(validate(graph), "Invalid id")


def test_unexpected_vertex_label():
    graph = load_graph("unexpected_vertex_label")
    assert_invalid(validate(graph), "Unexpected label")


def test_unexpected_edge_label():
    graph = load_graph("unexpected_edge_label")
    assert_invalid(validate(graph), "Unexpected label")


def test_property_value_type_mismatch():
    graph = load_graph("property_value_type_mismatch")
    assert_invalid(validate(graph), "Invalid value")


def test_unexpected_property_key():
    graph = load_graph("unexpected_property_key")
    assert_invalid(validate(graph), "Unexpected key")


def test_wrong_in_vertex_label():
    graph = load_graph("wrong_in_vertex_label")
    assert_invalid(validate(graph), "Wrong in-vertex label")


def test_wrong_out_vertex_label():
    graph = load_graph("wrong_out_vertex_label")
    assert_invalid(validate(graph), "Wrong out-vertex label")


def test_missing_required_edge_property():
    graph = load_graph("missing_required_edge_property")
    assert_invalid(validate(graph), "Missing value for")


def test_unknown_edge_endpoint():
    graph = load_graph("unknown_edge_endpoint")
    assert_invalid(validate(graph), "does not exist")
