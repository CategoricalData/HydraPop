"""Smoke test to verify the build setup."""

import hydra.core
import hydra.pg.model as pg_model
import hydra.pg.validation as pg_validation


def test_imports():
    assert pg_model.Graph is not None
    assert pg_validation.validate_graph is not None
    assert hydra.core.LiteralTypeString is not None
