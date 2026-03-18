"""Pytest configuration for HydraPop Python tests.

Patches hydra.lib.maybes.maybe to support lazy default arguments (lambdas).
The hydra.pg.validation module (generated from Hydra 0.14.0+) passes lambdas
as the default argument, but hydra-python 0.13.0's maybe() treats them as
plain values. This patch adds lazy evaluation support.

This patch can be removed once hydra-python >= 0.14.0 is available.
"""

import hydra.lib.maybes

_original_maybe = hydra.lib.maybes.maybe


def _lazy_maybe(default, f, x):
    result = _original_maybe(default, f, x)
    return result() if callable(result) else result


hydra.lib.maybes.maybe = _lazy_maybe
