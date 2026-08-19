"""Test package marker.

Note: `python -m unittest discover -s ml/tests` treats `ml/tests` itself as
the top-level directory, so this `__init__.py` does not run as a parent
package import before each test module the way a normal subpackage would --
each `test_*.py` file bootstraps `sys.path` itself at import time instead
(see the top of any test module in this directory).
"""
