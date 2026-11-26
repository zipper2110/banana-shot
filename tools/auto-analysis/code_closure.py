"""The code files of a stage: the static import closure of its entry modules (G12 follow-up, S2 item 13).

A stage key with a hand list of files misses a module that the stage imports through another module (S1 found:
feat_wasb imports serve_ball, and serve_ball.py was not in the flights key). This module reads the import lines of
each local module (a .py file in this folder; also an import inside a function) and follows them. It does not run
the code. It finds more files than the run uses (an import in a function that the stage does not call): such a
file can only make a stage run again, never give a stale output.

Use: files("ball_full", "net_events") -> sorted list of paths; modules(...) -> sorted list of names.
bench.py (S1) can use the same function for its lab keys."""
import ast, os

HERE = os.path.dirname(os.path.abspath(__file__))
_DEPS = {}


def _deps(mod, folder=HERE):
    """The local modules that mod imports (by its source text)."""
    key = (folder, mod)
    if key not in _DEPS:
        tree = ast.parse(open(os.path.join(folder, mod + ".py"), encoding="utf-8").read())
        out = set()
        for n in ast.walk(tree):
            if isinstance(n, ast.Import):
                names = [a.name for a in n.names]
            elif isinstance(n, ast.ImportFrom) and n.level == 0 and n.module:
                names = [n.module]
            else:
                continue
            for m in names:
                m = m.split(".")[0]
                if os.path.exists(os.path.join(folder, m + ".py")):
                    out.add(m)
        _DEPS[key] = out
    return _DEPS[key]


def modules(*entries, folder=HERE):
    """The entry modules and all local modules that they import, directly or through other modules."""
    seen, todo = set(), list(entries)
    while todo:
        m = todo.pop()
        if m not in seen:
            seen.add(m)
            todo += sorted(_deps(m, folder))
    return sorted(seen)


def files(*entries, folder=HERE):
    """The paths of modules(*entries)."""
    return [os.path.join(folder, m + ".py") for m in modules(*entries, folder=folder)]
