r"""A developer's differential comparison of the calculator (`kotlin/calc`) with SymPy (BSD); never shipped.

Random expressions per area (simplify, expand, factor, derivative, integral, limit, solve) are typed as a learner types them
(`derivative(x^2 sin(x), x)`), worked out by the engine as the Calculator works them out (`gradlew :calc:differential`, which
answers each line as JSON with its expressions in SymPy's syntax) and by SymPy, and compared numerically at points in mpmath.
Where the two disagree, the task itself decides at 40 digits: the original formula for simplify, expand and factor, mpmath's own
derivative for derivatives and antiderivatives, mpmath's limit, the formula at each root for solve. Each disagreement is
reported with the side that is wrong (the engine, SymPy, or both), and what one side declined while the other answered.

The later areas (7 November 2026) are compared the same way: integrals in several variables ("multiple", over boxes and in polar
coordinates, against mpmath's own quadrature), Fourier transforms ("fourier", F(ω) = ∫ f(t)e^(−iωt) dt, SymPy's transform at
ω/(2π), mpmath's quadrature at points of ω), distributions ("distribution", P(a ≤ X ≤ b) by sympy.stats, mpmath's incomplete gamma
and beta functions), and matrix functions ("matrix": det, inverse, rank, charpoly, adjugate, exactly in Python's fractions).

Usage: python -I tools/calc_differential.py --out <dir> [--count 120] [--seed 1] [--threads 8] [--build-root <dir>] [--areas a,b]
Needs sympy and mpmath (`pip install sympy mpmath`), in a virtual environment of its own.
"""
import argparse
import json
import multiprocessing
import os
import pathlib
import random
import subprocess
import sys
import time

import mpmath
import sympy as sp

X = sp.Symbol("x")
AREAS = ("simplify", "expand", "factor", "derivative", "integral", "limit", "solve", "multiple", "fourier", "distribution", "matrix")
LATER = ("multiple", "fourier", "distribution", "matrix")
Y = sp.Symbol("y")
T = sp.Symbol("t")
RR = sp.Symbol("r")
TH = sp.Symbol("theta")
KK = sp.Symbol("k")
SYMPY_SECONDS = 20


# ----------------------------------------------------------------------------------------------------------------------
# Typing an expression as a learner types it.

def typed(e):
    """[e] in the Calculator's typed syntax: explicit *, ^ for powers, e^(...), ln, sqrt, arctan, abs."""
    if e.is_Integer:
        return str(e) if e >= 0 else f"({e})"
    if e.is_Rational:
        return f"({e.p}/{e.q})"
    if e is sp.pi:
        return "pi"
    if e is sp.E:
        return "e"
    if e.is_Symbol:
        return e.name
    if e.is_Add:
        out = ""
        for t in sp.Add.make_args(e):
            s = typed(t)
            if not out:
                out = s
            elif s.startswith("(-") and s.endswith(")") and t.is_Number:
                out += " - " + s[2:-1]
            else:
                out += " + " + s
        return f"({out})"
    if e.is_Mul:
        return "(" + "*".join(typed(f) for f in sp.Mul.make_args(e)) + ")"
    if e.is_Pow:
        base, exp = e.args
        if exp == sp.Rational(1, 2):
            return f"sqrt({typed(base)})"
        if base is sp.E:
            return f"e^({typed(exp)})"
        return f"({typed(base)})^({typed(exp)})"
    if isinstance(e, sp.exp):
        return f"e^({typed(e.args[0])})"
    names = {sp.sin: "sin", sp.cos: "cos", sp.tan: "tan", sp.log: "ln", sp.atan: "arctan", sp.Abs: "abs", sp.sinh: "sinh",
             sp.cosh: "cosh", sp.tanh: "tanh", sp.asin: "arcsin", sp.acos: "arccos"}
    for f, name in names.items():
        if isinstance(e, f):
            return f"{name}({typed(e.args[0])})"
    raise ValueError(f"cannot type {e!r}")


# ----------------------------------------------------------------------------------------------------------------------
# Random tasks.

def small(r, lo=-5, hi=5, nonzero=False):
    while True:
        v = r.randint(lo, hi)
        if v or not nonzero:
            return v


def poly(r, degree, lo=-5, hi=5):
    return sum(small(r, lo, hi) * X ** k for k in range(degree)) + small(r, 1, 3) * X ** degree


def atom(r):
    k = r.randrange(6)
    if k == 0:
        return X
    if k == 1:
        return X ** r.randint(2, 4)
    if k == 2:
        return small(r, 1, 4) * X + small(r, -4, 4)
    if k == 3:
        return X ** 2 + small(r, 1, 4)
    if k == 4:
        return sp.Integer(small(r, 1, 6))
    return small(r, 1, 3) * X


def elementary(r, depth=2):
    """A random function of x: compositions of sin, cos, exp, ln (of something positive), sqrt, arctan and powers."""
    if depth == 0:
        return atom(r)
    k = r.randrange(11)
    u = elementary(r, depth - 1)
    if k == 0:
        return sp.sin(u)
    if k == 1:
        return sp.cos(u)
    if k == 2:
        return sp.exp(u if r.random() < 0.5 else -u)
    if k == 3:
        return sp.log(u ** 2 + 1)
    if k == 4:
        return sp.sqrt(u ** 2 + small(r, 1, 3))
    if k == 5:
        return sp.atan(u)
    if k == 6:
        return u ** r.randint(2, 3)
    v = elementary(r, depth - 1)
    if k == 7:
        return u + v
    if k == 8:
        return u - v
    if k == 9:
        return u * v
    return u / (v ** 2 + 1)


def tasks(area, count, r):
    """(typed line, the formula f in SymPy, extra) for [area]."""
    out = []
    while len(out) < count:
        if area == "simplify":
            k = r.randrange(4)
            if k == 0:
                common = X - small(r)
                p = sp.expand(poly(r, r.randint(1, 2)) * common)
                q = sp.expand(poly(r, r.randint(1, 2)) * common)
                f = p / q
            elif k == 1:
                a, b = elementary(r, 1), elementary(r, 1)
                f = sp.sin(a) ** 2 + sp.cos(a) ** 2 + b
            elif k == 2:
                f = sp.log(X ** 2 + small(r, 1, 4)) - sp.log(X ** 2 + small(r, 1, 4)) + sp.exp(sp.log(X ** 2 + 1))
            else:
                f = elementary(r, 2) + elementary(r, 1) - elementary(r, 1)
            out.append((f"simplify({typed(f)})", f, None))
        elif area == "expand":
            f = sp.Mul(*[poly(r, r.randint(1, 2)) for _ in range(r.randint(2, 3))]) ** r.randint(1, 2)
            out.append((f"expand({typed(f)})", f, None))
        elif area == "factor":
            factors = [poly(r, r.randint(1, 2), -4, 4) for _ in range(r.randint(2, 3))]
            f = sp.expand(sp.Mul(*factors))
            if f.is_number:
                continue
            out.append((f"factor({typed(f)})", f, None))
        elif area == "derivative":
            f = elementary(r, 2)
            out.append((f"derivative({typed(f)}, x)", f, None))
        elif area == "integral":
            k = r.randrange(5)
            if k == 0:
                f = X ** r.randint(0, 4) * sp.exp(small(r, -3, 3, True) * X)
            elif k == 1:
                f = X ** r.randint(0, 3) * sp.sin(small(r, 1, 4) * X) + sp.cos(small(r, 1, 3) * X) ** r.randint(1, 3)
            elif k == 2:
                num = poly(r, r.randint(0, 1))
                den = sp.expand((X - small(r)) * (X - small(r) - r.randint(1, 3)))
                f = num / den
            elif k == 3:
                f = small(r, 1, 3) / (X ** 2 + small(r, 1, 9)) + X / (X ** 2 + small(r, 1, 9))
            else:
                f = elementary(r, 1) * elementary(r, 0)
            out.append((f"integral({typed(f)}, x)", f, None))
        elif area == "limit":
            k = r.randrange(5)
            if k == 0:
                a = small(r)
                g = elementary(r, 1)
                f = (g - g.subs(X, a)) / (X - a)
                point = a
            elif k == 1:
                c = small(r, 1, 5)
                f = sp.sin(c * X) / X if r.random() < 0.5 else (1 - sp.cos(c * X)) / X ** 2
                point = 0
            elif k == 2:
                f = poly(r, 2) / poly(r, 2)
                point = sp.oo
            elif k == 3:
                f = (1 + sp.Integer(small(r, 1, 4)) / X) ** (small(r, 1, 3) * X)
                point = sp.oo
            else:
                f = elementary(r, 2)
                point = sp.Rational(small(r, -4, 4), 3)
            point = sp.sympify(point)
            pt = "infinity" if point == sp.oo else typed(point)
            out.append((f"limit({typed(f)}, x, {pt})", f, point))
        elif area == "solve":
            k = r.randrange(4)
            if k == 0:
                f = sp.expand(sp.Mul(*[X - small(r, -6, 6) for _ in range(r.randint(2, 4))]))
            elif k == 1:
                f = poly(r, r.randint(2, 5))
            elif k == 2:
                f = sp.exp(small(r, 1, 3) * X) - small(r, 1, 9)
            else:
                f = sp.log(X + small(r, 1, 5)) - small(r, -2, 2)
            out.append((f"solve({typed(f)} = 0, x)", f, None))
        elif area == "multiple":
            terms = [small(r, -3, 3, True) * X ** r.randint(0, 2) * Y ** r.randint(0, 2) for _ in range(r.randint(1, 3))]
            g = sp.Add(*terms)
            k = r.randrange(3)
            if k == 1:
                g = g * sp.exp(small(r, -1, 1, True) * X)
            elif k == 2:
                g = g + sp.sin(X * Y)
            if r.random() < 0.3:
                radius = small(r, 1, 2)
                out.append((f"polarintegral({typed(g)}, r, 0, {radius}, theta, 0, 2pi)", g, ["polar", radius]))
            else:
                a, c = small(r, -1, 1), small(r, -1, 1)
                b, d = a + small(r, 1, 2), c + small(r, 1, 2)
                out.append((f"integral({typed(g)}, x, {a}, {b}, y, {c}, {d})", g, ["box", a, b, c, d]))
        elif area == "fourier":
            a = small(r, 1, 4)
            b = small(r, 1, 3)
            g = [sp.exp(-a * sp.Abs(X)), sp.exp(-a * X ** 2), 1 / (a ** 2 + X ** 2), X * sp.exp(-a * X ** 2), sp.exp(-a * X ** 2) * sp.cos(b * X)][r.randrange(5)]
            out.append((f"fouriertransform({typed(g).replace('x', 't')})", g.subs(X, T), None))
        elif area == "distribution":
            k = r.randrange(5)
            lo = r.choice([0, 0.5, 1])
            hi = lo + r.choice([0.5, 1, 2, 3.5])
            if k == 0:
                nu = r.choice([1, 2, 3, 5, 8, 12])
                lo = r.choice([-2, -1, 0])
                out.append((f"tcdf({lo}, {hi}, {nu})", None, ["t", lo, hi, nu]))
            elif k == 1:
                dof = r.choice([1, 2, 3, 4, 6, 9])
                out.append((f"chisqcdf({lo}, {hi}, {dof})", None, ["chi", lo, hi, dof]))
            elif k == 2:
                d1, d2 = r.choice([1, 2, 4, 6]), r.choice([2, 3, 5, 10])
                out.append((f"fcdf({lo}, {hi}, {d1}, {d2})", None, ["f", lo, hi, d1, d2]))
            elif k == 3:
                shape, scale = r.choice([1, 2, 3, 0.5, 1.5]), r.choice([1, 2, 0.5])
                out.append((f"gammacdf({lo}, {hi}, {shape}, {scale})", None, ["gamma", lo, hi, shape, scale]))
            else:
                al, be = r.choice([1, 2, 3, 0.5]), r.choice([1, 2, 4, 1.5])
                lo, hi = r.choice([0, 0.1, 0.25]), r.choice([0.5, 0.75, 0.9])
                out.append((f"betacdf({lo}, {hi}, {al}, {be})", None, ["beta", lo, hi, al, be]))
        elif area == "matrix":
            n = r.randint(2, 3)
            m = [[r.randint(-4, 4) for _ in range(n)] for _ in range(n)]
            fn = r.choice(["det", "inverse", "rank", "charpoly", "adjugate"])
            text = "[" + ", ".join("[" + ", ".join(map(str, row)) + "]" for row in m) + "]"
            out.append((f"{fn}({text})", None, [fn, m]))
    return out


# ----------------------------------------------------------------------------------------------------------------------
# SymPy's answers, each within a time limit (in a process of its own: SymPy may not stop).

def sympy_answer(area, f, extra):
    try:
        if area == "simplify":
            return [str(sp.simplify(f))]
        if area == "expand":
            return [str(sp.expand(f))]
        if area == "factor":
            return [str(sp.factor(f)), int(len(sp.factor_list(f)[1])), int(sum(m for _, m in sp.factor_list(f)[1]))]
        if area == "derivative":
            return [str(sp.diff(f, X))]
        if area == "integral":
            g = sp.integrate(f, X)
            return None if g.has(sp.Integral) else [str(g)]
        if area == "limit":
            return [str(sp.limit(f, X, extra))]
        if area == "multiple":
            if extra[0] == "polar":
                g = f.subs({X: RR * sp.cos(TH), Y: RR * sp.sin(TH)}) * RR
                v = sp.integrate(g, (RR, 0, extra[1]), (TH, 0, 2 * sp.pi))
            else:
                _, a, b, c, d = extra
                v = sp.integrate(f, (X, a, b), (Y, c, d))
            return None if v.has(sp.Integral) else [str(v)]
        if area == "fourier":
            v = sp.fourier_transform(f, T, KK)
            return None if v.has(sp.FourierTransform) or v.has(sp.Integral) else [str(v)]
        if area == "distribution":
            import sympy.stats as st
            kind = extra[0]
            if kind == "t":
                xd = st.StudentT("X", extra[3])
            elif kind == "chi":
                xd = st.ChiSquared("X", extra[3])
            elif kind == "f":
                xd = st.FDistribution("X", extra[3], extra[4])
            elif kind == "gamma":
                xd = st.Gamma("X", sp.nsimplify(extra[3]), sp.nsimplify(extra[4]))
            else:
                xd = st.Beta("X", sp.nsimplify(extra[3]), sp.nsimplify(extra[4]))
            c = st.cdf(xd)
            return [str(sp.N(c(sp.nsimplify(extra[2])) - c(sp.nsimplify(extra[1])), 30))]
        if area == "matrix":
            fn, m = extra
            mat = sp.Matrix(m)
            if fn == "det":
                return [str(mat.det())]
            if fn == "rank":
                return [str(mat.rank())]
            if fn == "charpoly":
                return [str(mat.charpoly(sp.Symbol("λ")).as_expr())]
            if fn == "adjugate":
                return [str(mat.adjugate())]
            return None if mat.det() == 0 else [str(mat.inv())]
        if area == "solve":
            s = sp.solveset(f, X, sp.S.Reals)
            if isinstance(s, sp.FiniteSet):
                return [str(v) for v in s]
            return None
    except Exception as e:  # noqa: BLE001 — SymPy's own failure is reported as such
        return ["error", repr(e)[:200]]
    return None


def _sympy_job(args):
    area, f, extra = args
    if area in LATER:
        return sympy_answer(area, sp.sympify(f) if f is not None else None, extra)
    return sympy_answer(area, sp.sympify(f), sp.sympify(extra) if extra is not None else None)


# ----------------------------------------------------------------------------------------------------------------------
# Numbers in mpmath.

mpmath.mp.dps = 40
NS = {name: getattr(sp, name) for name in dir(sp) if not name.startswith("_")}


def parse(s):
    return None if s is None else sp.sympify(s, locals=NS)


_LAMBDAS = {}


def value(e, x):
    """[e] at x in mpmath at 40 digits, or None where it is not real and finite there."""
    key = sp.srepr(e)
    if key not in _LAMBDAS:
        try:
            _LAMBDAS[key] = sp.lambdify(X, e, modules="mpmath")
        except Exception:  # noqa: BLE001
            _LAMBDAS[key] = None
    fn = _LAMBDAS[key]
    if fn is None:
        return None
    try:
        v = mpmath.mpmathify(fn(x))
    except Exception:  # noqa: BLE001 — outside its domain
        return None
    if isinstance(v, mpmath.mpc):
        if abs(v.imag) > mpmath.mpf("1e-25") * max(1, abs(v.real)):
            return None
        v = v.real
    return v if mpmath.isfinite(v) else None


def at(e):
    """[e] as a function for mpmath: NaN where it is not real."""
    return lambda t: (lambda v: mpmath.nan if v is None else v)(value(e, t))


def number(e):
    """A constant of SymPy's as an mpmath number at 40 digits, or None where it is not a real finite number."""
    try:
        if e is None or not e.is_number:
            return None
        v = sp.N(e, 40)
        c = complex(v)
        if abs(c.imag) > 1e-25 * max(1.0, abs(c.real)) or not mpmath.isfinite(c.real):
            return None
        return mpmath.mpf(str(sp.re(v)))
    except Exception:  # noqa: BLE001
        return None


POINTS = [mpmath.mpf(p) for p in ("0.3137", "-0.7713", "1.4159", "-1.9021", "2.2361", "0.8812", "-2.6458", "1.7321")]


def equal(a, b, points=POINTS):
    """Whether a and b agree at every point where both are real (at least three); None where too few points compare."""
    compared = 0
    for p in points:
        va, vb = value(a, p), value(b, p)
        if va is None or vb is None:
            continue
        if abs(va - vb) > mpmath.mpf("1e-9") * max(1, abs(va), abs(vb)):
            return False
        compared += 1
    return True if compared >= 3 else None


def derivative_matches(g, f):
    """Whether g′ = f at the points, g′ by mpmath's own numerical derivative."""
    compared = 0
    for p in POINTS:
        vf = value(f, p)
        if vf is None:
            continue
        try:
            d = mpmath.diff(at(g), p)
        except Exception:  # noqa: BLE001
            continue
        if not mpmath.isfinite(d):
            continue
        if abs(d - vf) > mpmath.mpf("1e-8") * max(1, abs(vf)):
            return False
        compared += 1
    return True if compared >= 3 else None


def limit_value(f, point):
    """f's limit by mpmath (Richardson on values ever nearer the point), or None where it does not settle."""
    try:
        if point == sp.oo:
            v = mpmath.limit(at(f), mpmath.inf)
        else:
            a = mpmath.mpf(str(sp.N(point, 40)))
            left = mpmath.limit(at(f), a, direction=-1)
            right = mpmath.limit(at(f), a, direction=1)
            if abs(left - right) > mpmath.mpf("1e-8") * max(1, abs(left)):
                return None
            v = (left + right) / 2
        return v if mpmath.isfinite(v) else None
    except Exception:  # noqa: BLE001
        return None


def roots_hold(f, roots):
    """Whether every root makes f vanish at 40 digits."""
    for root in roots:
        r = number(root)
        v = None if r is None else value(f, r)
        # A decimal root (found numerically) holds to its own 16 digits; an exact one to 40.
        tolerance = mpmath.mpf("1e-9") if root.has(sp.Float) else mpmath.mpf("1e-20")
        if v is None or abs(v) > tolerance * max(1, abs(r)) ** 6:
            return False
    return True


def real_roots(f):
    """f's distinct real roots by mpmath: a polynomial's from all the roots of its square-free part, an exponential's or a
    logarithm's from the sign changes on a fine grid."""
    out = []
    if f.is_polynomial(X):
        coefficients = [complex(c) for c in sp.Poly(sp.sqf_part(f), X).all_coeffs()]
        if len(coefficients) > 1:
            out = [mpmath.re(z) for z in mpmath.polyroots(coefficients, maxsteps=800, extraprec=400) if abs(mpmath.im(z)) < 1e-20]
    else:
        grid = [mpmath.mpf(k) / 40 for k in range(-800, 801)]
        vals = [value(f, t) for t in grid]
        for (a, va), (b, vb) in zip(zip(grid, vals), zip(grid[1:], vals[1:])):
            if va is not None and vb is not None and (va == 0 or va * vb < 0):
                out.append(a if va == 0 else mpmath.findroot(at(f), (a, b), solver="anderson"))
    distinct = []
    for v in sorted(out):
        if not distinct or abs(v - distinct[-1]) > mpmath.mpf("1e-15") * max(1, abs(v)):
            distinct.append(v)
    return distinct


def same_set(a, b):
    a = sorted(a)
    b = sorted(b)
    return len(a) == len(b) and all(abs(x - y) < mpmath.mpf("1e-12") * max(1, abs(x)) for x, y in zip(a, b))


# ----------------------------------------------------------------------------------------------------------------------
# The verdicts.

# ----------------------------------------------------------------------------------------------------------------------
# The later areas' truths, computed without either side: fractions, mpmath's incomplete gamma and beta functions, its quadrature.

def fraction_det(m):
    from fractions import Fraction
    a = [[Fraction(v) for v in row] for row in m]
    n = len(a)
    d = Fraction(1)
    for c in range(n):
        p = next((i for i in range(c, n) if a[i][c] != 0), None)
        if p is None:
            return Fraction(0)
        if p != c:
            a[c], a[p] = a[p], a[c]
            d = -d
        d *= a[c][c]
        for i in range(c + 1, n):
            f = a[i][c] / a[c][c]
            a[i] = [a[i][j] - f * a[c][j] for j in range(n)]
    return d


def fraction_rank(m):
    from fractions import Fraction
    a = [[Fraction(v) for v in row] for row in m]
    rank = 0
    for c in range(len(a[0])):
        p = next((i for i in range(rank, len(a)) if a[i][c] != 0), None)
        if p is None:
            continue
        a[rank], a[p] = a[p], a[rank]
        for i in range(len(a)):
            if i != rank and a[i][c] != 0:
                f = a[i][c] / a[rank][c]
                a[i] = [a[i][j] - f * a[rank][j] for j in range(len(a[0]))]
        rank += 1
    return rank


def matrix_truth(fn, m):
    """The matrix function in fractions: a number, a matrix (rows), or det(λI − A) at λ = 0 … n + 1; None for no inverse."""
    from fractions import Fraction
    n = len(m)
    if fn == "det":
        return fraction_det(m)
    if fn == "rank":
        return Fraction(fraction_rank(m))
    if fn == "charpoly":
        return [fraction_det([[(lam if i == j else 0) - m[i][j] for j in range(n)] for i in range(n)]) for lam in range(n + 2)]
    # adj(A)ᵢⱼ = (−1)^(i+j)·det of A without row j and column i.
    adj = [[(-1) ** (i + j) * fraction_det([[m[r][c] for c in range(n) if c != i] for r in range(n) if r != j]) for j in range(n)] for i in range(n)]
    if fn == "adjugate":
        return adj
    d = fraction_det(m)
    return None if d == 0 else [[v / d for v in row] for row in adj]


def matrix_value(fn, e, n):
    """A side's answer in fractions, as [matrix_truth] gives it; None where it cannot be read."""
    from fractions import Fraction
    if e is None:
        return None
    try:
        if fn == "charpoly":
            letters = list(e.free_symbols)
            lam = letters[0] if letters else sp.Symbol("λ")
            return [Fraction(str(sp.nsimplify(e.subs(lam, v)))) for v in range(n + 2)]
        if isinstance(e, sp.MatrixBase):
            return [[Fraction(str(v)) for v in e.row(i)] for i in range(e.rows)]
        return Fraction(str(sp.nsimplify(e)))
    except Exception:  # noqa: BLE001
        return None


def distribution_truth(extra):
    kind = extra[0]
    lo, hi = mpmath.mpf(extra[1]), mpmath.mpf(extra[2])
    if kind == "t":
        nu = mpmath.mpf(extra[3])

        def cdf(x):
            tail = mpmath.betainc(nu / 2, mpmath.mpf(1) / 2, 0, nu / (nu + x * x), regularized=True) / 2
            return 1 - tail if x > 0 else tail
    elif kind == "chi":
        k = mpmath.mpf(extra[3])
        cdf = lambda x: 0 if x <= 0 else mpmath.gammainc(k / 2, 0, x / 2, regularized=True)  # noqa: E731
    elif kind == "f":
        a, b = mpmath.mpf(extra[3]), mpmath.mpf(extra[4])
        cdf = lambda x: 0 if x <= 0 else mpmath.betainc(a / 2, b / 2, 0, a * x / (a * x + b), regularized=True)  # noqa: E731
    elif kind == "gamma":
        k, th = mpmath.mpf(extra[3]), mpmath.mpf(extra[4])
        cdf = lambda x: 0 if x <= 0 else mpmath.gammainc(k, 0, x / th, regularized=True)  # noqa: E731
    else:
        a, b = mpmath.mpf(extra[3]), mpmath.mpf(extra[4])
        cdf = lambda x: mpmath.betainc(a, b, 0, min(max(x, 0), 1), regularized=True)  # noqa: E731
    return cdf(hi) - cdf(lo)


def multiple_truth(f, extra):
    g = sp.lambdify((X, Y), f, modules="mpmath")
    if extra[0] == "polar":
        return mpmath.quad(lambda rr, t: g(rr * mpmath.cos(t), rr * mpmath.sin(t)) * rr, [0, extra[1]], [0, 2 * mpmath.pi])
    _, a, b, c, d = extra
    return mpmath.quad(g, [a, b], [c, d])


OMEGAS = [mpmath.mpf(w) for w in ("-2.3", "-0.7", "0.4", "1.1", "3.6")]


def fourier_truth(f, w):
    """∫ f(t)e^(−iωt) dt as ∫₀^∞ (f(t) + f(−t))cos ωt dt − i∫₀^∞ (f(t) − f(−t))sin ωt dt, each by mpmath's quadrature for
    oscillating integrands (1/(1 + t²) falls off too slowly for a plain quadrature of e^(−iωt))."""
    g = sp.lambdify(T, f, modules="mpmath")
    a = abs(w)
    even = mpmath.quadosc(lambda t: (g(t) + g(-t)) * mpmath.cos(a * t), [0, mpmath.inf], omega=a)
    odd = mpmath.quadosc(lambda t: (g(t) - g(-t)) * mpmath.sin(a * t), [0, mpmath.inf], omega=a)
    return mpmath.mpc(even, -odd if w > 0 else odd)


def complex_value(e, symbol, w):
    try:
        v = complex(sp.N(e.subs(symbol, sp.Float(str(w), 30)), 30))
        return mpmath.mpc(v.real, v.imag)
    except Exception:  # noqa: BLE001
        return None


def judge_later(area, f, extra, engine, sym):
    kind = engine["kind"]
    declined = kind in ("Failed", "Crash")
    exprs = [parse(x) for x in engine.get("exprs") or []]
    sym_ok = sym is not None and sym[0] != "error"
    if area == "matrix":
        fn, m = extra
        truth = matrix_truth(fn, m)
        e_val = None if declined or not exprs else matrix_value(fn, exprs[0], len(m))
        s_val = matrix_value(fn, parse(sym[0]), len(m)) if sym_ok else None
        e_ok = (True if truth is None and declined else None) if e_val is None else e_val == truth
        s_ok = None if s_val is None else s_val == truth
    elif area in ("distribution", "multiple"):
        truth = distribution_truth(extra) if area == "distribution" else multiple_truth(f, extra)
        e_val = None if declined or not exprs else number(exprs[0])
        s_val = number(parse(sym[0])) if sym_ok else None
        tol = mpmath.mpf("1e-9" if area == "distribution" else "1e-8") * max(1, abs(truth))
        e_ok = None if e_val is None else abs(e_val - truth) <= tol
        s_ok = None if s_val is None else abs(s_val - truth) <= tol
    elif area == "fourier":
        e_expr = None if declined or not exprs else exprs[0]
        s_expr = parse(sym[0]) if sym_ok else None

        def holds(expr, symbol, scale):
            for w in OMEGAS:
                t = fourier_truth(f, w)
                v = complex_value(expr, symbol, w * scale)
                if v is None or abs(v - t) > mpmath.mpf("1e-8") * max(1, abs(t)):
                    return False
            return True
        # The engine's transform is in ω (its only letter); SymPy's ∫ f(t)e^(−2πikt) dt is F(ω) at k = ω/(2π).
        e_ok = None if e_expr is None else holds(e_expr, (list(e_expr.free_symbols) or [sp.Symbol("w")])[0], 1)
        s_ok = None if s_expr is None else holds(s_expr, KK, 1 / (2 * mpmath.pi))
    else:
        raise ValueError(area)
    if e_ok is None and declined:
        return ("both-declined" if not sym_ok else "engine-declined"), engine["answer"]
    if e_ok is False and s_ok is False:
        return "both-wrong", ""
    if e_ok is False:
        return "engine-wrong", ""
    if s_ok is False:
        return "sympy-wrong", ""
    if e_ok and not sym_ok:
        return "sympy-declined", (sym[1] if sym else "no answer")
    if e_ok and s_ok:
        return "agree", ""
    if e_ok and s_ok is None:
        return "agree", "SymPy's answer not comparable"
    return "unclear", f"engine {e_ok}, SymPy {s_ok}"


def judge(area, f, extra, engine, sym):
    """(verdict, note): agree, engine-declined, sympy-declined, both-declined, engine-wrong, sympy-wrong, both-wrong, reading,
    or unclear."""
    if area in LATER:
        return judge_later(area, f, extra, engine, sym)
    kind = engine["kind"]
    declined = kind in ("Failed", "Crash")
    reading = engine.get("reading")
    if reading and area in ("simplify", "expand", "factor", "derivative", "integral") and len(reading) == 1 and reading[0]:
        if equal(parse(reading[0]), f) is False:
            return "reading", f"the engine read {reading[0]}"
    exprs = [parse(s) for s in engine.get("exprs") or []]
    sym_ok = sym is not None and sym[0] != "error"
    if area in ("simplify", "expand", "factor"):
        e_ok = None if declined or not exprs or exprs[0] is None else equal(exprs[0], f)
        s_ok = equal(parse(sym[0]), f) if sym_ok else None
        if area == "factor" and e_ok and sym_ok and engine.get("complete"):
            # Fewer factors than SymPy finds over the rationals, marked complete, is a wrong claim.
            got = sp.factor_list(exprs[0])[1]
            if sum(m for _, m in got) < sym[2]:
                return "engine-wrong", f"marked complete with {sum(m for _, m in got)} factors; SymPy {sym[2]}"
    elif area == "derivative":
        e_ok = None if declined or not exprs or exprs[0] is None else derivative_matches(f, exprs[0])
        s_ok = derivative_matches(f, parse(sym[0])) if sym_ok else None
    elif area == "integral":
        e_ok = None if declined or not exprs or exprs[0] is None else derivative_matches(exprs[0], f)
        s_ok = derivative_matches(parse(sym[0]), f) if sym_ok else None
    elif area == "limit":
        truth = limit_value(f, extra)
        e_val = None if declined or not exprs else number(exprs[0])
        s_val = number(parse(sym[0])) if sym_ok else None
        if truth is None:
            e_ok = s_ok = None
            if e_val is not None and s_val is not None and abs(e_val - s_val) > 1e-8 * max(1, abs(s_val)):
                return "unclear", f"engine {e_val}, SymPy {s_val}, mpmath does not settle"
        else:
            e_ok = None if e_val is None else abs(e_val - truth) <= mpmath.mpf("1e-7") * max(1, abs(truth))
            s_ok = None if s_val is None else abs(s_val - truth) <= mpmath.mpf("1e-7") * max(1, abs(truth))
    elif area == "solve":
        truth = real_roots(f)
        e_vals = None if declined or kind != "Solved" or engine.get("periodic") else [number(v) for v in exprs]
        e_ok = None if e_vals is None else roots_hold(f, exprs) and same_set(e_vals, truth)
        s_vals = [parse(v) for v in sym] if sym_ok else None
        s_ok = None if s_vals is None else roots_hold(f, s_vals) and same_set([number(v) for v in s_vals], truth)
    else:
        raise ValueError(area)
    if e_ok is None and declined:
        return ("both-declined" if not sym_ok else "engine-declined"), engine["answer"]
    if e_ok is False and s_ok is False:
        return "both-wrong", ""
    if e_ok is False:
        return "engine-wrong", ""
    if s_ok is False:
        return "sympy-wrong", ""
    if e_ok and not sym_ok:
        return "sympy-declined", (sym[1] if sym else "no answer")
    if e_ok and s_ok:
        return "agree", ""
    if e_ok and s_ok is None:
        # SymPy's answer not comparable on the reals (log(x − 3) for x < 3 is complex), the engine's right.
        return "agree", "SymPy's answer not real at the points"
    return "unclear", f"engine {e_ok}, SymPy {s_ok}"


# ----------------------------------------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--count", type=int, default=120, help="tasks per area")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--threads", type=int, default=8)
    parser.add_argument("--build-root", default=None, help="the engine's Gradle build directory (outside the repository)")
    parser.add_argument("--areas", default=None, help="a comma-separated choice of the areas")
    args = parser.parse_args()
    areas = tuple(a for a in AREAS if args.areas is None or a in args.areas.split(","))
    args.out.mkdir(parents=True, exist_ok=True)
    root = pathlib.Path(__file__).resolve().parent.parent
    r = random.Random(args.seed)
    all_tasks = []
    for area in areas:
        for k, (text, f, extra) in enumerate(tasks(area, args.count, r)):
            if area in LATER:
                all_tasks.append({"id": f"{area}-{k}", "area": area, "text": text, "f": None if f is None else str(f), "extra": extra})
            else:
                all_tasks.append({"id": f"{area}-{k}", "area": area, "text": text, "f": str(f), "extra": None if extra is None else str(extra)})
    (args.out / "tasks.txt").write_text("".join(f"{t['id']}\t{t['text']}\n" for t in all_tasks), encoding="utf-8")

    # The engine, all at once.
    gradle = root / "kotlin" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    build_root = args.build_root or str(pathlib.Path(os.environ.get("TEMP", "/tmp")) / "math-reader-differential")
    started = time.time()
    subprocess.run([str(gradle), ":calc:differential", f"-PmathReaderBuildRoot={build_root}", "-q",
                    f"--args={args.out / 'tasks.txt'} {args.out / 'engine.jsonl'} {args.threads}"], cwd=root / "kotlin", check=True)
    engine = {d["id"]: d for d in (json.loads(l) for l in (args.out / "engine.jsonl").read_text(encoding="utf-8").splitlines())}
    print(f"engine: {len(engine)} tasks in {time.time() - started:.0f} s")

    # SymPy, each in its time.
    started = time.time()
    sym = {}
    pool = multiprocessing.Pool(args.threads)
    pending = {t["id"]: pool.apply_async(_sympy_job, ((t["area"], t["f"], t["extra"]),)) for t in all_tasks}
    for t in all_tasks:
        try:
            sym[t["id"]] = pending[t["id"]].get(timeout=SYMPY_SECONDS)
        except multiprocessing.TimeoutError:
            sym[t["id"]] = ["error", f"SymPy took over {SYMPY_SECONDS} s"]
    pool.terminate()
    print(f"sympy: {len(sym)} tasks in {time.time() - started:.0f} s")

    # The verdicts.
    rows = []
    for t in all_tasks:
        f = parse(t["f"])
        extra = t["extra"] if t["area"] in LATER else parse(t["extra"])
        try:
            verdict, note = judge(t["area"], f, extra, engine[t["id"]], sym[t["id"]])
        except Exception as e:  # noqa: BLE001 — a comparison that cannot be made is reported, not hidden
            verdict, note = "unclear", f"comparison failed: {e!r}"[:200]
        rows.append({**t, "verdict": verdict, "note": note, "engine": engine[t["id"]]["answer"], "sympy": sym[t["id"]],
                     "ms": engine[t["id"]]["ms"]})
    (args.out / "verdicts.jsonl").write_text("".join(json.dumps(x, ensure_ascii=False, default=str) + "\n" for x in rows), encoding="utf-8")
    verdicts = ("agree", "engine-declined", "sympy-declined", "both-declined", "engine-wrong", "sympy-wrong", "both-wrong", "reading", "unclear")
    lines = [f"{'area':<11}" + "".join(f"{v:>16}" for v in verdicts)]
    for area in areas:
        mine = [x for x in rows if x["area"] == area]
        lines.append(f"{area:<11}" + "".join(f"{sum(1 for x in mine if x['verdict'] == v):>16}" for v in verdicts))
    lines.append("")
    for x in rows:
        if x["verdict"] not in ("agree", "engine-declined", "sympy-declined", "both-declined"):
            lines.append(f"[{x['verdict']}] {x['text']}\n    engine: {x['engine']}\n    sympy:  {x['sympy']}\n    {x['note']}")
    lines.append("")
    lines.append("engine declined:")
    for x in rows:
        if x["verdict"] == "engine-declined":
            lines.append(f"  {x['text']} → {x['engine']}; SymPy {x['sympy']}")
    report = "\n".join(lines)
    (args.out / "report.txt").write_text(report, encoding="utf-8")
    print(report.split("\n\n")[0])


if __name__ == "__main__":
    sys.exit(main())
