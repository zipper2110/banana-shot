"""The code files of a stage: the static import closure of its entry modules (G12 follow-up, S2 item 13).

A stage key with a hand list of files misses a module that the stage imports through another module (S1 found:
feat_wasb imports serve_ball, and serve_ball.py was not in the flights key). This module reads the import lines of
each local module (a .py file in this folder; also an import inside a function) and follows them. It does not run
the code. It finds more files than the run uses (an import in a function that the stage does not call): such a
file can only make a stage run again, never give a stale output.

Use: files("ball_full", "net_events") -> sorted list of paths; modules(...) -> sorted list of names.
bench.py (S1) can use the same function for its lab keys.

A39 (S2, E202): files() keys only the modules that the stage code can reach (reach), not each module that an import
line names. Cause: the court detector speed-up E186 changed only court_auto.py, and court_auto.py was in the flights
key, because court_refine.py imports it. The flights use court_refine only through court_refine.to_court, and that
function uses no court_auto code. So each lab and app flights cache was stale after a change with no effect on them.
modules() is still the full import closure (an upper bound; the hand lists of the ball and pose tags use it).
The rule of reach (a static analysis, the code does not run):
- The entry modules are reached in full.
- In a reached part of a module, a name of that module reaches its top-level definitions; mod.x and
  "from mod import x" reach x of mod; a module name used as a value (not mod.x), "import *", globals(), vars(), eval
  and exec reach the full module.
- A module that the run imports (an import line at the top of an imported module, or in a reached part) runs its
  top-level statements that are not pure definitions (calls, statements with blocks, a store into an attribute or an
  item, a call of a local function or of a local module at import time): these are reached.
- A module is in the key if a part of it is reached. Its full file is in the key.
An imported module that the stage does not use (only pure definitions run) is not in the key."""
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


_INFO = {}
ALL = "*"
_DYNAMIC = {"globals", "vars", "eval", "exec", "locals"}


def _local(m, folder):
    return os.path.exists(os.path.join(folder, m.split(".")[0] + ".py"))


def _info(mod, folder):
    """(statements, {name: indexes of the statements that bind it}, indexes of the statements that run at import and
    are not pure definitions, {name: local modules that an import line binds to it}) of a local module."""
    path = os.path.join(folder, mod + ".py")
    st = os.stat(path)
    key = (folder, mod, st.st_mtime_ns, st.st_size)      # a changed file is read again
    if key in _INFO:
        return _INFO[key]
    with open(path, encoding="utf-8") as f:
        body = ast.parse(f.read()).body
    binds, roots, alias = {}, [], {}
    for n in ast.walk(ast.Module(body=body, type_ignores=[])):
        if isinstance(n, ast.Import):
            for a in n.names:
                if _local(a.name, folder):
                    alias.setdefault(a.asname or a.name.split(".")[0], set()).add(a.name.split(".")[0])
    for i, n in enumerate(body):
        names = []
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names = [n.name] + [g for x in ast.walk(n) if isinstance(x, ast.Global) for g in x.names]
        elif isinstance(n, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
            for t in (n.targets if isinstance(n, ast.Assign) else [n.target]):
                names += [x.id for x in ast.walk(t) if isinstance(x, ast.Name)]
        elif isinstance(n, (ast.Import, ast.ImportFrom)):
            names = [a.asname or a.name.split(".")[0] for a in n.names if a.name != "*"]
        for nm in names:
            binds.setdefault(nm, []).append(i)
    project = set(alias) | {a.asname or a.name for n in body if isinstance(n, ast.ImportFrom) and n.level == 0
                            and n.module and _local(n.module, folder) for a in n.names}
    local_names = set(binds) - {a.asname or a.name.split(".")[0] for n in body if isinstance(n, (ast.Import, ast.ImportFrom))
                                for a in n.names} | project

    def impure(exprs):
        # A call at import time of a local function or of a local module. A call of a library (numpy, os, ...) in a
        # definition is pure.
        for e in exprs:
            for x in ast.walk(e):
                if isinstance(x, ast.Call):
                    f = x.func
                    while isinstance(f, (ast.Attribute, ast.Call)):
                        f = f.value if isinstance(f, ast.Attribute) else f.func
                    if not isinstance(f, ast.Name) or f.id in local_names:
                        return True
        return False

    def import_time(n):
        # a decorator is a call at import time
        dec = [ast.Call(func=d, args=[], keywords=[]) for d in n.decorator_list]
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
            a = n.args
            return dec + a.defaults + [d for d in a.kw_defaults if d is not None]
        return dec + n.bases + [k.value for k in n.keywords]

    def pure(n):
        if isinstance(n, (ast.Import, ast.ImportFrom, ast.Pass)):
            return True
        if isinstance(n, ast.Expr):
            return isinstance(n.value, ast.Constant)                     # a docstring
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
            return not impure(import_time(n))
        if isinstance(n, ast.ClassDef):
            return not impure(import_time(n)) and all(pure(b) for b in n.body)
        if isinstance(n, (ast.Assign, ast.AnnAssign, ast.AugAssign)):
            ts = n.targets if isinstance(n, ast.Assign) else [n.target]
            if not all(isinstance(x, (ast.Name, ast.Tuple, ast.List, ast.Starred, ast.expr_context)) for t in ts for x in ast.walk(t)):
                return False                                             # a store into an attribute or an item
            return n.value is None or not impure([n.value])
        return False

    for i, n in enumerate(body):
        main = isinstance(n, ast.If) and "__name__" in ast.unparse(n.test) and "__main__" in ast.unparse(n.test)
        if not main and not pure(n):
            roots.append(i)
    _INFO[key] = (body, binds, roots, alias)
    return _INFO[key]


def reach(*entries, folder=HERE):
    """The local modules of which the run of the entry modules can use a part (A39, see the text of this module).
    A subset of modules(*entries)."""
    want = {m: {ALL} for m in entries}                  # module: the names that the run uses (ALL: the full module)
    ran, done = set(), {}                               # the imported modules; module: the reached statements
    todo = list(entries)

    def use(m, names):
        w = want.setdefault(m, set())
        if not set(names) <= w:
            w |= set(names)
            todo.append(m)

    def run(m):
        if m not in ran:
            ran.add(m)
            todo.append(m)

    def imports(x):
        if isinstance(x, ast.Import):
            for a in x.names:
                if _local(a.name, folder):
                    run(a.name.split(".")[0])
        elif isinstance(x, ast.ImportFrom) and x.level == 0 and x.module and _local(x.module, folder):
            run(x.module.split(".")[0])

    while todo:
        m = todo.pop()
        run(m)
        body, binds, roots, alias = _info(m, folder)
        seen = done.setdefault(m, set())
        w = want.get(m, set())
        for n in body:                                   # the top-level import lines run with the module
            imports(n)
        stack = sorted((set(range(len(body))) if ALL in w else set(roots) | {i for nm in w for i in binds.get(nm, ())}) - seen)
        while stack:
            i = stack.pop()
            if i in seen:
                continue
            seen.add(i)
            node = body[i]
            values = {id(x.value) for x in ast.walk(node) if isinstance(x, ast.Attribute)}
            for x in ast.walk(node):
                if isinstance(x, ast.Name):
                    stack += [j for j in binds.get(x.id, ()) if j not in seen]
                    if id(x) not in values:
                        for mod in alias.get(x.id, ()):
                            use(mod, [ALL])              # the module as a value: the full module
                elif isinstance(x, ast.Attribute) and isinstance(x.value, ast.Name):
                    for mod in alias.get(x.value.id, ()):
                        use(mod, [x.attr])
                imports(x)
                if isinstance(x, ast.ImportFrom) and x.level == 0 and x.module and _local(x.module, folder):
                    use(x.module.split(".")[0], [ALL if a.name == "*" else a.name for a in x.names])
                elif isinstance(x, ast.Call) and isinstance(x.func, ast.Name) and x.func.id in _DYNAMIC:
                    use(m, [ALL])
                elif isinstance(x, ast.Call) and (getattr(x.func, "id", None) == "__import__"
                                                  or getattr(x.func, "attr", None) == "import_module"):
                    for a in x.args[:1]:
                        if isinstance(a, ast.Constant) and isinstance(a.value, str) and _local(a.value, folder):
                            run(a.value.split(".")[0])
                            use(a.value.split(".")[0], [ALL])
    return sorted(m for m in ran if done.get(m) or want.get(m))


def files(*entries, folder=HERE):
    """The paths of reach(*entries) (A39; before A39: of modules(*entries))."""
    return [os.path.join(folder, m + ".py") for m in reach(*entries, folder=folder)]
