"""Round-trip tests for hydra_to_gremlin and gremlin_to_hydra.

These tests require a running Gremlin Server with an empty, writable
TinkerGraph at ws://localhost:8182/gremlin and a traversal source named
``g`` registered. They are skipped automatically when no such server is
reachable, so they are safe to run in CI environments without a server.

Verifies:

- A single-vertex graph written via ``hydra_to_gremlin`` reads back
  byte-equal via ``gremlin_to_hydra``.
- A graph with vertices and an edge round-trips identically.
- Re-writing the same graph is idempotent (counts don't grow).
- Re-writing with new property values overwrites (no duplicates, latest
  values win).
"""

import socket

import hydra.core as core
import hydra.pg.model as pg
import pytest
from hydra.dsl.python import FrozenDict

from hydrapop.gremlin_bridge import gremlin_to_hydra, hydra_to_gremlin


GREMLIN_URL = "ws://localhost:8182/gremlin"


def _server_reachable() -> bool:
    try:
        with socket.create_connection(("localhost", 8182), timeout=0.5):
            return True
    except OSError:
        return False


pytestmark = pytest.mark.skipif(
    not _server_reachable(),
    reason="Gremlin Server not reachable at " + GREMLIN_URL,
)


@pytest.fixture
def g():
    """Provide a clean traversal source. Drops all data before and after
    each test to isolate cases."""
    from gremlin_python.driver.driver_remote_connection import (
        DriverRemoteConnection,
    )
    from gremlin_python.process.anonymous_traversal import traversal

    conn = DriverRemoteConnection(GREMLIN_URL, "g")
    try:
        g = traversal().with_remote(conn)
        g.V().drop().iterate()
        yield g
        g.V().drop().iterate()
    finally:
        conn.close()


def _lit_str(s: str):
    return core.LiteralString(s)


def test_vertex_only_round_trip(g):
    vid = _lit_str("Symptom:photophobia")
    vertex = pg.Vertex(
        label=pg.VertexLabel("Symptom"),
        id=vid,
        properties=FrozenDict({pg.PropertyKey("value"): _lit_str("photophobia")}),
    )
    graph = pg.Graph(
        vertices=FrozenDict({vid: vertex}),
        edges=FrozenDict({}),
    )

    hydra_to_gremlin(graph, g)
    got = gremlin_to_hydra(g)

    assert len(got.vertices) == 1
    assert len(got.edges) == 0
    got_v = next(iter(got.vertices.values()))
    assert got_v.label.value == "Symptom"
    assert got_v.id.value == "Symptom:photophobia"
    assert got_v.properties[pg.PropertyKey("value")].value == "photophobia"


def test_vertex_and_edge_round_trip(g):
    h_id = _lit_str("h-1")
    t_id = _lit_str("Trigger:caffeine")
    e_id = _lit_str("h-1-triggers->Trigger:caffeine")
    headache = pg.Vertex(
        label=pg.VertexLabel("Headache"), id=h_id,
        properties=FrozenDict({pg.PropertyKey("description"): _lit_str("first")}),
    )
    trigger = pg.Vertex(
        label=pg.VertexLabel("Trigger"), id=t_id,
        properties=FrozenDict({
            pg.PropertyKey("value"): _lit_str("caffeine"),
            pg.PropertyKey("category"): _lit_str("ingested"),
        }),
    )
    edge = pg.Edge(
        label=pg.EdgeLabel("triggers"), id=e_id,
        out=h_id, in_=t_id, properties=FrozenDict({}),
    )
    graph = pg.Graph(
        vertices=FrozenDict({h_id: headache, t_id: trigger}),
        edges=FrozenDict({e_id: edge}),
    )

    hydra_to_gremlin(graph, g)
    got = gremlin_to_hydra(g)

    assert len(got.vertices) == 2
    assert len(got.edges) == 1
    got_e = next(iter(got.edges.values()))
    assert got_e.label.value == "triggers"
    assert got_e.out.value == "h-1"
    assert got_e.in_.value == "Trigger:caffeine"


def test_re_write_is_idempotent(g):
    vid = _lit_str("Symptom:nausea")
    vertex = pg.Vertex(
        label=pg.VertexLabel("Symptom"), id=vid,
        properties=FrozenDict({pg.PropertyKey("value"): _lit_str("nausea")}),
    )
    graph = pg.Graph(
        vertices=FrozenDict({vid: vertex}),
        edges=FrozenDict({}),
    )

    hydra_to_gremlin(graph, g)
    hydra_to_gremlin(graph, g)
    hydra_to_gremlin(graph, g)

    got = gremlin_to_hydra(g)
    assert len(got.vertices) == 1
    assert len(got.edges) == 0


def test_edge_with_missing_endpoints_creates_stubs(g):
    """An edge that references vertices not yet written should not crash;
    placeholder vertices are created so the edge still lands."""
    out_id = _lit_str("v-out")
    in_id = _lit_str("v-in")
    e_id = _lit_str("v-out-likes->v-in")
    edge = pg.Edge(
        label=pg.EdgeLabel("likes"), id=e_id,
        out=out_id, in_=in_id, properties=FrozenDict({}),
    )
    # No vertices in this graph; only the edge.
    graph = pg.Graph(
        vertices=FrozenDict({}),
        edges=FrozenDict({e_id: edge}),
    )

    hydra_to_gremlin(graph, g)

    got = gremlin_to_hydra(g)
    # Two stub vertices + one edge.
    assert len(got.vertices) == 2
    assert len(got.edges) == 1
    for v in got.vertices.values():
        assert v.label.value == "_stub"


def test_upsert_overwrites_properties(g):
    vid = _lit_str("h-x")

    v1 = pg.Vertex(
        label=pg.VertexLabel("Headache"), id=vid,
        properties=FrozenDict({pg.PropertyKey("description"): _lit_str("initial")}),
    )
    hydra_to_gremlin(
        pg.Graph(vertices=FrozenDict({vid: v1}), edges=FrozenDict({})),
        g,
    )

    v2 = pg.Vertex(
        label=pg.VertexLabel("Headache"), id=vid,
        properties=FrozenDict({pg.PropertyKey("description"): _lit_str("updated")}),
    )
    hydra_to_gremlin(
        pg.Graph(vertices=FrozenDict({vid: v2}), edges=FrozenDict({})),
        g,
    )

    got = gremlin_to_hydra(g)
    assert len(got.vertices) == 1
    got_v = next(iter(got.vertices.values()))
    assert got_v.properties[pg.PropertyKey("description")].value == "updated"
