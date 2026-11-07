r"""The written corpus's reference values from mpmath (BSD), at 30 digits; a developer's tool, never shipped.

Writes `kotlin/calc/src/test/kotlin/com/professoreuler/mathcalc/written/WrittenTables.kt`: the distributions' densities, tails and
quantiles (t, χ², F, gamma, beta, lognormal, exponential), the statistics of hypothesis tests and confidence intervals on random
data (their statistic, degrees of freedom, p-value, critical value and ends), and special functions (Ei, Si, Ci, Yₙ, K, E, B). Each
value is mpmath's own: its incomplete gamma and beta functions, its quadrature, its findroot for a quantile. The tasks are seeded,
so the file is the same each time.

Usage: python -I tools/corpus_tables.py   (needs mpmath: `pip install mpmath`)
"""
import pathlib
import random
import sys

import mpmath as mp

mp.mp.dps = 40


def s(v):
    """A value as Kotlin reads it: 25 significant digits."""
    v = mp.mpf(v)
    if v == 0:
        return "0.0"
    return mp.nstr(v, 25, min_fixed=-mp.inf, max_fixed=mp.inf).replace("e", "E")


def num(v):
    """A number as the keys type it: 2.5, -1, 0.75."""
    if isinstance(v, str):
        return v
    t = repr(v) if isinstance(v, float) else str(v)
    return t[:-2] if t.endswith(".0") else t


# ----------------------------------------------------------------------------------------------------------------------
# The distributions: density, P(a ≤ X ≤ b) and the quantile.

def t_pdf(x, nu):
    return mp.gamma((nu + 1) / 2) / (mp.sqrt(nu * mp.pi) * mp.gamma(nu / 2)) * (1 + x * x / nu) ** (-(nu + 1) / 2)


def t_cdf(x, nu):
    if x == mp.inf:
        return mp.mpf(1)
    if x == -mp.inf:
        return mp.mpf(0)
    z = nu / (nu + x * x)
    tail = mp.betainc(nu / 2, mp.mpf(1) / 2, 0, z, regularized=True) / 2
    return 1 - tail if x > 0 else tail


def chi_pdf(x, k):
    return 0 if x < 0 else x ** (k / 2 - 1) * mp.exp(-x / 2) / (2 ** (k / 2) * mp.gamma(k / 2))


def chi_cdf(x, k):
    if x == mp.inf:
        return mp.mpf(1)
    return 0 if x <= 0 else mp.gammainc(k / 2, 0, x / 2, regularized=True)


def f_pdf(x, a, b):
    if x <= 0:
        return 0
    return mp.sqrt((a * x) ** a * b ** b / (a * x + b) ** (a + b)) / (x * mp.beta(a / 2, b / 2))


def f_cdf(x, a, b):
    if x == mp.inf:
        return mp.mpf(1)
    return 0 if x <= 0 else mp.betainc(a / 2, b / 2, 0, a * x / (a * x + b), regularized=True)


def gamma_pdf(x, k, th):
    return 0 if x < 0 else x ** (k - 1) * mp.exp(-x / th) / (mp.gamma(k) * th ** k)


def gamma_cdf(x, k, th):
    if x == mp.inf:
        return mp.mpf(1)
    return 0 if x <= 0 else mp.gammainc(k, 0, x / th, regularized=True)


def beta_pdf(x, a, b):
    return 0 if x < 0 or x > 1 else x ** (a - 1) * (1 - x) ** (b - 1) / mp.beta(a, b)


def beta_cdf(x, a, b):
    return mp.betainc(a, b, 0, min(max(x, 0), 1), regularized=True)


def logn_pdf(x, mu, sg):
    return 0 if x <= 0 else mp.exp(-(mp.log(x) - mu) ** 2 / (2 * sg * sg)) / (x * sg * mp.sqrt(2 * mp.pi))


def logn_cdf(x, mu, sg):
    if x == mp.inf:
        return mp.mpf(1)
    return 0 if x <= 0 else mp.ncdf((mp.log(x) - mu) / sg)


def exp_pdf(x, lam):
    return 0 if x < 0 else lam * mp.exp(-lam * x)


def exp_cdf(x, lam):
    if x == mp.inf:
        return mp.mpf(1)
    return 0 if x <= 0 else -mp.expm1(-lam * x)


def between(cdf, a, b, *p):
    return cdf(b, *p) - cdf(a, *p)


def quantile(cdf, p, lo, hi, *q):
    """The x with cdf(x) = p, by bisection to 40 digits (mpmath's arithmetic; a bracket [lo, hi] given)."""
    lo, hi = mp.mpf(lo), mp.mpf(hi)
    while cdf(hi, *q) < p:
        hi *= 2
    for _ in range(400):
        mid = (lo + hi) / 2
        if cdf(mid, *q) < p:
            lo = mid
        else:
            hi = mid
    return (lo + hi) / 2


def bound(v):
    return "infinity" if v == mp.inf else ("-infinity" if v == -mp.inf else num(v))


def distributions(r):
    rows = []
    halves = [0.25, 0.5, 0.75, 1.5, 2.5]
    for _ in range(40):
        nu = r.choice([1, 2, 3, 4, 5, 7, 10, 15, 24, 30, 2.5])
        x = r.choice([-3.5, -2, -1.25, -0.5, 0, 0.4, 1, 1.7, 2.25, 3, 5])
        rows.append((f"tpdf({num(x)}, {num(nu)})", t_pdf(mp.mpf(x), mp.mpf(nu))))
        a = r.choice([-mp.inf, -4, -2.5, -1, -0.3, 0])
        b = r.choice([0.2, 1, 1.5, 2, 3.5, mp.inf])
        rows.append((f"tcdf({bound(a)}, {bound(b)}, {num(nu)})", between(t_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(nu))))
        p = r.choice([0.6, 0.75, 0.9, 0.95, 0.975, 0.99, 0.995, 0.1, 0.025])
        rows.append((f"invt({num(p)}, {num(nu)})", quantile(t_cdf, mp.mpf(p), -1e3, 1e3, mp.mpf(nu))))
    for _ in range(40):
        k = r.choice([1, 2, 3, 4, 5, 6, 8, 10, 12, 20, 1.5])
        x = r.choice([0.1, 0.5, 1, 2, 3.3, 5, 8, 12, 20])
        rows.append((f"chisqpdf({num(x)}, {num(k)})", chi_pdf(mp.mpf(x), mp.mpf(k))))
        a = r.choice([0, 0.5, 1, 2, 4])
        b = r.choice([4.5, 6, 9.5, 15, mp.inf])
        rows.append((f"chisqcdf({num(a)}, {bound(b)}, {num(k)})", between(chi_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(k))))
        p = r.choice([0.05, 0.1, 0.5, 0.9, 0.95, 0.99])
        rows.append((f"invchisq({num(p)}, {num(k)})", quantile(chi_cdf, mp.mpf(p), 0, 100, mp.mpf(k))))
    for _ in range(40):
        d1 = r.choice([1, 2, 3, 4, 5, 8, 10])
        d2 = r.choice([2, 3, 5, 7, 10, 20, 30])
        x = r.choice([0.2, 0.5, 1, 1.5, 2.5, 4])
        rows.append((f"fpdf({num(x)}, {d1}, {d2})", f_pdf(mp.mpf(x), mp.mpf(d1), mp.mpf(d2))))
        a = r.choice([0, 0.3, 1])
        b = r.choice([1.5, 2, 3, 5, mp.inf])
        rows.append((f"fcdf({num(a)}, {bound(b)}, {d1}, {d2})", between(f_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(d1), mp.mpf(d2))))
        p = r.choice([0.1, 0.5, 0.9, 0.95, 0.99])
        rows.append((f"invf({num(p)}, {d1}, {d2})", quantile(f_cdf, mp.mpf(p), 0, 100, mp.mpf(d1), mp.mpf(d2))))
    for _ in range(40):
        k = r.choice([0.5, 1, 1.5, 2, 3, 4.5, 7])
        th = r.choice([0.5, 1, 2, 3])
        x = r.choice([0.25, 1, 2, 3.5, 6, 10])
        rows.append((f"gammapdf({num(x)}, {num(k)}, {num(th)})", gamma_pdf(mp.mpf(x), mp.mpf(k), mp.mpf(th))))
        a = r.choice([0, 0.5, 1, 2])
        b = r.choice([2.5, 4, 8, mp.inf])
        rows.append((f"gammacdf({num(a)}, {bound(b)}, {num(k)}, {num(th)})", between(gamma_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(k), mp.mpf(th))))
        p = r.choice([0.1, 0.25, 0.5, 0.9, 0.95])
        rows.append((f"invgamma({num(p)}, {num(k)}, {num(th)})", quantile(gamma_cdf, mp.mpf(p), 0, 100, mp.mpf(k), mp.mpf(th))))
    for _ in range(40):
        al = r.choice([0.5, 1, 1.5, 2, 3, 5])
        be = r.choice([0.5, 1, 2, 2.5, 4])
        x = r.choice([0.1, 0.25, 0.5, 0.7, 0.9])
        rows.append((f"betapdf({num(x)}, {num(al)}, {num(be)})", beta_pdf(mp.mpf(x), mp.mpf(al), mp.mpf(be))))
        a = r.choice([0, 0.1, 0.3])
        b = r.choice([0.5, 0.8, 1])
        rows.append((f"betacdf({num(a)}, {num(b)}, {num(al)}, {num(be)})", between(beta_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(al), mp.mpf(be))))
        p = r.choice([0.05, 0.3, 0.5, 0.8, 0.99])
        rows.append((f"invbeta({num(p)}, {num(al)}, {num(be)})", quantile(beta_cdf, mp.mpf(p), 0, 1, mp.mpf(al), mp.mpf(be))))
    for _ in range(30):
        mu = r.choice([-1, 0, 0.5, 1, 2])
        sg = r.choice([0.25, 0.5, 1, 1.5])
        x = r.choice([0.3, 1, 2, 4.5, 10])
        rows.append((f"lognormalpdf({num(x)}, {num(mu)}, {num(sg)})", logn_pdf(mp.mpf(x), mp.mpf(mu), mp.mpf(sg))))
        a = r.choice([0, 0.5, 1])
        b = r.choice([2, 3, 7, mp.inf])
        rows.append((f"lognormalcdf({num(a)}, {bound(b)}, {num(mu)}, {num(sg)})", between(logn_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(mu), mp.mpf(sg))))
        p = r.choice([0.1, 0.5, 0.75, 0.95])
        rows.append((f"invlognormal({num(p)}, {num(mu)}, {num(sg)})", quantile(logn_cdf, mp.mpf(p), 0, 1000, mp.mpf(mu), mp.mpf(sg))))
    for _ in range(20):
        lam = r.choice([0.5, 1, 2, 3, 0.25])
        x = r.choice([0, 0.5, 1, 2.5, 4])
        rows.append((f"exppdf({num(x)}, {num(lam)})", exp_pdf(mp.mpf(x), mp.mpf(lam))))
        a = r.choice([0, 0.5, 1])
        b = r.choice([1.5, 3, mp.inf])
        rows.append((f"expcdf({num(a)}, {bound(b)}, {num(lam)})", between(exp_cdf, mp.mpf(a), mp.mpf(b), mp.mpf(lam))))
        p = r.choice([0.1, 0.5, 0.9, 0.99])
        rows.append((f"invexp({num(p)}, {num(lam)})", -mp.log(1 - mp.mpf(p)) / mp.mpf(lam)))
    return rows


# ----------------------------------------------------------------------------------------------------------------------
# Tests and intervals on random data: (call, [(quantity, value)], reject).

def mean(xs):
    return mp.fsum(xs) / len(xs)


def sd(xs):
    m = mean(xs)
    return mp.sqrt(mp.fsum((x - m) ** 2 for x in xs) / (len(xs) - 1))


def data(r, n, centre, spread):
    return [mp.mpf(round(centre + spread * r.uniform(-1, 1), 1)) for _ in range(n)]


def listed(xs):
    return "[" + ", ".join(num(float(x)) for x in xs) + "]"


def tail(cdf, stat, alt, *p):
    if alt == "greater":
        return 1 - cdf(stat, *p)
    if alt == "less":
        return cdf(stat, *p)
    return 2 * min(cdf(stat, *p), 1 - cdf(stat, *p))


def critical(cdf, alpha, alt, lo, hi, *p):
    if alt == "greater":
        return quantile(cdf, 1 - alpha, lo, hi, *p)
    if alt == "less":
        return quantile(cdf, alpha, lo, hi, *p)
    return quantile(cdf, 1 - alpha / 2, lo, hi, *p)


def normal_cdf(x):
    return mp.ncdf(x)


def options(r):
    alpha = r.choice([None, None, 0.05, 0.01, 0.1])
    alt = r.choice([None, None, "less", "greater"])
    text = ("" if alpha is None else f", {num(alpha)}") + ("" if alt is None else f", {alt}")
    return mp.mpf(alpha if alpha is not None else 0.05), alt or "two", text


def tests(r):
    rows = []
    for k in range(40):
        n = r.randint(4, 14)
        xs = data(r, n, r.choice([5, 10, 50, 100]), r.choice([1, 3, 8]))
        mu0 = round(float(mean(xs)) + r.choice([-2, -1, -0.5, 0, 0.5, 1, 2]) * float(sd(xs)) / 2, 1)
        alpha, alt, opt = options(r)
        m, s_ = mean(xs), sd(xs)
        if s_ == 0:
            continue
        t = (m - mu0) / (s_ / mp.sqrt(n))
        p = tail(t_cdf, t, alt, mp.mpf(n - 1))
        c = critical(t_cdf, alpha, alt, -1e3, 1e3, mp.mpf(n - 1))
        rows.append((f"ttest({listed(xs)}, {num(mu0)}{opt})", [("STATISTIC", t), ("DF", n - 1), ("P_VALUE", p), ("CRITICAL", c)], p < alpha))
        # The same from its summary.
        sm, ss = round(float(m), 2), round(float(s_), 2)
        t2 = (mp.mpf(sm) - mu0) / (mp.mpf(ss) / mp.sqrt(n))
        p2 = tail(t_cdf, t2, alt, mp.mpf(n - 1))
        rows.append((f"ttest({num(sm)}, {num(ss)}, {n}, {num(mu0)}{opt})", [("STATISTIC", t2), ("DF", n - 1), ("P_VALUE", p2)], p2 < alpha))
        sigma = r.choice([1, 2, 5, 10])
        z = (m - mu0) / (mp.mpf(sigma) / mp.sqrt(n))
        pz = tail(normal_cdf, z, alt)
        cz = critical(normal_cdf, alpha, alt, -40, 40)
        rows.append((f"ztest({listed(xs)}, {num(mu0)}, {sigma}{opt})", [("STATISTIC", z), ("P_VALUE", pz), ("CRITICAL", cz)], pz < alpha))
        rows.append((f"ztest({num(sm)}, {n}, {num(mu0)}, {sigma}{opt})",
                     [("STATISTIC", (mp.mpf(sm) - mu0) / (mp.mpf(sigma) / mp.sqrt(n)))], None))
    for k in range(30):
        a = data(r, r.randint(3, 10), 20, 4)
        b = data(r, r.randint(3, 10), r.choice([18, 20, 23]), r.choice([2, 4, 6]))
        if sd(a) == 0 or sd(b) == 0:
            continue
        alpha, alt, opt = options(r)
        v1, v2 = sd(a) ** 2 / len(a), sd(b) ** 2 / len(b)
        t = (mean(a) - mean(b)) / mp.sqrt(v1 + v2)
        df = (v1 + v2) ** 2 / (v1 ** 2 / (len(a) - 1) + v2 ** 2 / (len(b) - 1))
        p = tail(t_cdf, t, alt, df)
        rows.append((f"welchtest({listed(a)}, {listed(b)}{opt})", [("STATISTIC", t), ("DF", df), ("P_VALUE", p)], p < alpha))
    for k in range(30):
        cats = r.randint(2, 5)
        probs = [r.randint(1, 5) for _ in range(cats)]
        total = sum(probs)
        observed = [r.randint(5, 40) for _ in range(cats)]
        n = sum(observed)
        if k % 2 == 0:
            expected = [mp.mpf(p_) / total * n for p_ in probs]
            given = ", ".join(f"{p_}/{total}" for p_ in probs)
        else:
            # Counts that add up to the total: equal ones where they divide it.
            if n % cats:
                observed[0] += cats - n % cats
                n = sum(observed)
            expected = [mp.mpf(n // cats)] * cats
            given = ", ".join(str(n // cats) for _ in range(cats))
        chi = mp.fsum((o - e) ** 2 / e for o, e in zip(observed, expected))
        p = 1 - chi_cdf(chi, mp.mpf(cats - 1))
        rows.append((f"chisqgof([{', '.join(map(str, observed))}], [{given}])", [("STATISTIC", chi), ("DF", cats - 1), ("P_VALUE", p)], p < 0.05))
    for k in range(30):
        rr, cc = r.randint(2, 3), r.randint(2, 4)
        table = [[r.randint(3, 30) for _ in range(cc)] for _ in range(rr)]
        rs = [sum(row) for row in table]
        cs = [sum(table[i][j] for i in range(rr)) for j in range(cc)]
        n = sum(rs)
        chi = mp.fsum((mp.mpf(table[i][j]) - mp.mpf(rs[i] * cs[j]) / n) ** 2 / (mp.mpf(rs[i] * cs[j]) / n) for i in range(rr) for j in range(cc))
        df = (rr - 1) * (cc - 1)
        p = 1 - chi_cdf(chi, mp.mpf(df))
        text = "[" + ", ".join("[" + ", ".join(map(str, row)) + "]" for row in table) + "]"
        rows.append((f"chisqindep({text})", [("STATISTIC", chi), ("DF", df), ("P_VALUE", p)], p < 0.05))
    for k in range(40):
        n = r.randint(4, 20)
        xs = data(r, n, 30, 5)
        level = r.choice([0.9, 0.95, 0.99, 0.8])
        m, s_ = mean(xs), sd(xs)
        if s_ == 0:
            continue
        c = quantile(t_cdf, (1 + mp.mpf(level)) / 2, 0, 1e3, mp.mpf(n - 1))
        rows.append((f"tinterval({listed(xs)}, {num(level)})", [("LOWER", m - c * s_ / mp.sqrt(n)), ("UPPER", m + c * s_ / mp.sqrt(n)), ("CRITICAL", c)], None))
        sm, ss = round(float(m), 2), round(float(s_), 2)
        rows.append((f"tinterval({num(sm)}, {num(ss)}, {n}, {num(level)})",
                     [("LOWER", sm - c * ss / mp.sqrt(n)), ("UPPER", sm + c * ss / mp.sqrt(n))], None))
        sigma = r.choice([1, 2, 4])
        cz = quantile(normal_cdf, (1 + mp.mpf(level)) / 2, 0, 40)
        rows.append((f"zinterval({listed(xs)}, {sigma}, {num(level)})", [("LOWER", m - cz * sigma / mp.sqrt(n)), ("UPPER", m + cz * sigma / mp.sqrt(n))], None))
        rows.append((f"zinterval({num(sm)}, {n}, {sigma}, {num(level)})", [("LOWER", sm - cz * sigma / mp.sqrt(n)), ("UPPER", sm + cz * sigma / mp.sqrt(n))], None))
        trials = r.randint(10, 400)
        succ = r.randint(1, trials - 1)
        ph = mp.mpf(succ) / trials
        se = mp.sqrt(ph * (1 - ph) / trials)
        centre = (ph + cz * cz / (2 * trials)) / (1 + cz * cz / trials)
        half = cz * mp.sqrt(ph * (1 - ph) / trials + cz * cz / (4 * trials * trials)) / (1 + cz * cz / trials)
        rows.append((f"propinterval({succ}, {trials}, {num(level)})", [("LOWER", ph - cz * se), ("UPPER", ph + cz * se),
                     ("WILSON_LOWER", centre - half), ("WILSON_UPPER", centre + half)], None))
    return rows


# ----------------------------------------------------------------------------------------------------------------------
# Special functions.

def special(r):
    rows = []
    for x in [0.1, 0.25, 0.5, 1, 1.5, 2, 3, 4.5, 6, 10, -0.5, -1, -2, -4]:
        rows.append((f"Ei({num(x)})", mp.ei(x)))
        rows.append((f"Si({num(x)})", mp.si(x)))
        if x > 0:
            rows.append((f"Ci({num(x)})", mp.ci(x)))
    for n in range(0, 6):
        for x in [0.25, 0.5, 1, 2, 3.5, 5, 8, 12]:
            rows.append((f"bessely({n}, {num(x)})", mp.bessely(n, x)))
    for k in [0, 0.1, 0.25, 0.3, 0.5, 0.6, 0.75, 0.9, 0.95, 0.99, -0.5]:
        # mpmath's ellipk and ellipe take the parameter m = k².
        rows.append((f"elliptick({num(k)})", mp.ellipk(mp.mpf(k) ** 2)))
        rows.append((f"elliptice({num(k)})", mp.ellipe(mp.mpf(k) ** 2)))
    for a in [0.5, 1.5, 2.5, 0.25, 3, 4.5, 1.2]:
        for b in [0.5, 1, 2.5, 0.75, 3]:
            rows.append((f"beta({num(a)}, {num(b)})", mp.beta(a, b)))
    return rows


def kotlin(distributions_rows, test_rows, special_rows):
    def pair(call, v):
        return f'        "{call}" to "{s(v)}",'

    out = ["package com.professoreuler.mathcalc", "",
           "/*", " * Generated by tools/corpus_tables.py from mpmath (BSD) at 40 digits: the written corpus's reference values for the",
           " * distributions, the tests and intervals, and the special functions. Do not edit; run the tool again.", " */",
           "internal object WrittenTables {",
           "    /** A distribution's call and its value. */",
           "    val DISTRIBUTIONS: List<Pair<String, String>> = listOf("]
    out += [pair(c, v) for c, v in distributions_rows]
    out += ["    )", "",
            "    /** A test's or interval's call, its quantities (name, value) and whether H₀ is rejected (null: not checked). */",
            "    val TESTS: List<Triple<String, List<Pair<String, String>>, Boolean?>> = listOf("]
    for c, q, rej in test_rows:
        qs = ", ".join(f'"{name}" to "{s(v)}"' for name, v in q)
        out.append(f'        Triple("{c}", listOf({qs}), {"null" if rej is None else str(bool(rej)).lower()}),')
    out += ["    )", "", "    /** A special function's call and its value. */", "    val SPECIAL: List<Pair<String, String>> = listOf("]
    out += [pair(c, v) for c, v in special_rows]
    out += ["    )", "}", ""]
    return "\n".join(out)


def main():
    r = random.Random(1107)
    text = kotlin(distributions(r), tests(r), special(r))
    root = pathlib.Path(__file__).resolve().parent.parent
    target = root / "kotlin" / "calc" / "src" / "test" / "kotlin" / "com" / "professoreuler" / "mathcalc" / "written" / "WrittenTables.kt"
    target.write_text(text, encoding="utf-8")
    print(f"wrote {target} ({text.count(chr(10))} lines)")


if __name__ == "__main__":
    sys.exit(main())
