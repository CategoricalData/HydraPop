"""Round-trip tests for hydrapop.encode.

Loads the canonical JSON files produced by Java's GenerateExampleData, decodes
them via hydrapop.decode, re-encodes via hydrapop.encode, and asserts the
re-encoded JSON is structurally identical to the original.
"""

import json
import os

from hydrapop.decode import decode_graph, decode_graph_schema
from hydrapop.encode import encode_graph, encode_graph_schema


def _data_dir():
    # __file__ is src/test/python/hydrapop/test_encode.py
    project_root = os.path.dirname(os.path.dirname(os.path.dirname(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))))))
    return os.path.join(project_root, "src", "gen-main", "json")


def _load(name):
    with open(os.path.join(_data_dir(), name + ".json")) as f:
        return json.load(f)


def _sort_top(doc, key_to_str):
    """Sort vertices and edges by @key for stable comparison."""
    return {
        "vertices": sorted(doc["vertices"], key=lambda e: key_to_str(e["@key"])),
        "edges": sorted(doc["edges"], key=lambda e: key_to_str(e["@key"])),
    }


def test_schema_round_trip():
    original = _load("schema")
    schema = decode_graph_schema(original)
    encoded = encode_graph_schema(schema)
    # Schema map keys are bare strings (vertex/edge labels).
    assert _sort_top(original, str) == _sort_top(encoded, str)


GRAPH_FIXTURES = [
    "modern_graph",
    "missing_required_property",
    "missing_required_edge_property",
    "property_value_type_mismatch",
    "unexpected_edge_label",
    "unexpected_property_key",
    "unexpected_vertex_label",
    "unknown_edge_endpoint",
    "wrong_id_type",
    "wrong_in_vertex_label",
    "wrong_out_vertex_label",
]


def test_graph_round_trips():
    # Graph map keys are encoded literals (objects); sort by their JSON form.
    def key_to_str(k):
        return json.dumps(k, sort_keys=True)

    for name in GRAPH_FIXTURES:
        original = _load(name)
        graph = decode_graph(original)
        encoded = encode_graph(graph)
        assert _sort_top(original, key_to_str) == _sort_top(encoded, key_to_str), (
            f"Round-trip mismatch for {name}"
        )
