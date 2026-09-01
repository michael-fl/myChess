"""Monotone least squares for evaluation-feature screens, with a block bootstrap.

Extracted from `king-attack-vs-stockfish.py` so it does not depend on any one feature. That tool
reaches the engine through `KingAttackProbe`, which reads `WeightingFunction.ATTACK_UNIT_*` and
therefore only compiles on branch `attack-units`; the mathematics below has no such tie, and
neither does `king-safety-screen.py`, which uses it to screen candidates on master. Keeping the
two apart is the difference between a method that survives a shelved branch and one that does
not.

**The problem.** A feature is an ordinal index — attack units, a storm level, a bucketed exposure
count — and the question is what each level is worth in centipawns. Fit

    minimise  ||X b - y||^2      subject to  0 <= b1 <= b2 <= ... <= bN

where each row of `X` carries `+phase/MAX_PHASE` at white's index and `-phase/MAX_PHASE` at
black's, and `y` is whatever the caller is trying to explain — in practice a stronger evaluator's
score minus this engine's.

**Why constrained.** Unconstrained fits of these features fall somewhere in their range, which
would score more danger as less. Repairing that afterwards by hand invents numbers; making it a
constraint of the fit does not. Where the constraint binds, pool-adjacent-violators merges the
offending indices into one level, so equal neighboring entries are the measurement saying it
cannot separate them.

**How to read the output.** Zero lies on the boundary of the constraint set, so a point estimate
near it is not evidence — only a bootstrap lower bound above zero is. And a **flat** result is a
reliable stop signal for a candidate feature, while a strong one is not a promise: a static term
also has to survive the search, the clock and its own cost.

@author Michael Fleischhauer
"""

import random

MAX_INDEX = 8           # free parameters; index 0 is pinned at zero and is not one
MAX_PHASE = 24
CLIP_CP = 2000          # a handful of extreme evaluations must not dominate least squares
BLOCK = 8               # consecutive corpus lines come from the same game
REPLICATES = 200
PGD_STEPS = 20000
PGD_TOL = 1e-9
PERCENTILES = (5, 95)


def features(index_white, index_black, phase):
    """Phase-scaled indicator row: danger to black is positive, danger to white negative."""
    row = [0.0] * MAX_INDEX
    scale = phase / MAX_PHASE

    if index_white > 0:
        row[min(index_white, MAX_INDEX) - 1] += scale

    if index_black > 0:
        row[min(index_black, MAX_INDEX) - 1] -= scale

    return row


def block_normals(rows, block=BLOCK):
    """Per-block (X'X, X'y), so a bootstrap replicate is a sum rather than a re-scan."""
    blocks = []

    for start in range(0, len(rows), block):
        left = [[0.0] * MAX_INDEX for _ in range(MAX_INDEX)]
        right = [0.0] * MAX_INDEX

        for row, target in rows[start:start + block]:
            for i in range(MAX_INDEX):
                if row[i] == 0.0:
                    continue

                right[i] += row[i] * target

                for j in range(MAX_INDEX):
                    left[i][j] += row[i] * row[j]

        blocks.append((left, right))

    return blocks


def combine(blocks, counts):
    """Sum the normal equations of the blocks drawn `counts[i]` times each."""
    left = [[0.0] * MAX_INDEX for _ in range(MAX_INDEX)]
    right = [0.0] * MAX_INDEX

    for (block_left, block_right), times in zip(blocks, counts):
        if not times:
            continue

        for i in range(MAX_INDEX):
            right[i] += times * block_right[i]

            for j in range(MAX_INDEX):
                left[i][j] += times * block_left[i][j]

    return left, right


def pava(values):
    """Projection onto the isotonic cone: pool adjacent violators, equal weights."""
    pooled, weights = [], []

    for value in values:
        pooled.append(value)
        weights.append(1.0)

        while len(pooled) > 1 and pooled[-2] > pooled[-1]:
            weight = weights[-2] + weights[-1]
            mean = (pooled[-2] * weights[-2] + pooled[-1] * weights[-1]) / weight
            pooled[-2:] = [mean]
            weights[-2:] = [weight]

    out = []

    for mean, weight in zip(pooled, weights):
        out.extend([mean] * int(weight))

    return out


def solve_isotonic(left, right, start=None):
    """Projected gradient descent over {0 <= b1 <= ... <= bN}; returns the curve and the steps."""
    lipschitz = max(sum(abs(value) for value in row) for row in left) or 1.0
    step = 1.0 / lipschitz
    current = list(start) if start else [0.0] * MAX_INDEX

    for taken in range(PGD_STEPS):
        gradient = [sum(left[i][j] * current[j] for j in range(MAX_INDEX)) - right[i]
                    for i in range(MAX_INDEX)]
        nxt = [max(0.0, value)
               for value in pava([current[i] - step * gradient[i] for i in range(MAX_INDEX)])]
        shift = max(abs(nxt[i] - current[i]) for i in range(MAX_INDEX))
        current = nxt

        if shift < PGD_TOL:
            return current, taken + 1

    return current, PGD_STEPS


def solve_free(left, right):
    """Unconstrained normal equations, for the reference column."""
    matrix = [row[:] + [right[i]] for i, row in enumerate(left)]

    for column in range(MAX_INDEX):
        pivot = max(range(column, MAX_INDEX), key=lambda r: abs(matrix[r][column]))
        matrix[column], matrix[pivot] = matrix[pivot], matrix[column]

        if abs(matrix[column][column]) < 1e-12:
            continue

        for row in range(MAX_INDEX):
            if row == column:
                continue

            factor = matrix[row][column] / matrix[column][column]

            for k in range(column, MAX_INDEX + 1):
                matrix[row][k] -= factor * matrix[column][k]

    return [matrix[i][MAX_INDEX] / matrix[i][i] if abs(matrix[i][i]) > 1e-12 else 0.0
            for i in range(MAX_INDEX)]


def residual(left, right, curve, total):
    """Sum of squared residuals; `total` is the sum of squares of the targets."""
    quadratic = sum(curve[i] * sum(left[i][j] * curve[j] for j in range(MAX_INDEX))
                    for i in range(MAX_INDEX))

    return quadratic - 2 * sum(right[i] * curve[i] for i in range(MAX_INDEX)) + total


def percentile(values, share):
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(share / 100.0 * (len(ordered) - 1)))))

    return ordered[index]


def fit(rows, total, replicates=REPLICATES, seed=20260901):
    """Point estimate, bootstrap draws and diagnostics for one feature.

    :param rows: ``(feature row, target)`` pairs, in corpus order — the block bootstrap relies on
                 that order, because consecutive corpus lines come from the same game
    :param total: sum of squares of the targets, for the residual
    :return: dict with ``curve``, ``free``, ``draws``, ``residual``, ``no_term``, ``steps``,
             ``feasible``
    """
    blocks = block_normals(rows)
    left, right = combine(blocks, [1] * len(blocks))
    free = solve_free(left, right)
    curve, steps = solve_isotonic(left, right)
    rng = random.Random(seed)
    draws = [[] for _ in range(MAX_INDEX)]

    for _ in range(replicates):
        counts = [0] * len(blocks)

        for _ in range(len(blocks)):
            counts[rng.randrange(len(blocks))] += 1

        replicate_left, replicate_right = combine(blocks, counts)
        estimate, _ = solve_isotonic(replicate_left, replicate_right, start=curve)

        for k in range(MAX_INDEX):
            draws[k].append(estimate[k])

    feasible = (all(curve[i] >= curve[i - 1] - 1e-9 for i in range(1, MAX_INDEX))
                and min(curve) >= -1e-9)

    return {"curve": curve, "free": free, "draws": draws, "steps": steps, "feasible": feasible,
            "residual": residual(left, right, curve, total), "no_term": total}


def report(label, fitted, shares=None):
    """Print one candidate's table and return its rounded curve."""
    explained = 100 * (fitted["no_term"] - fitted["residual"]) / fitted["no_term"]
    print(f"\n=== {label} ===  erklaerte Residualvarianz {explained:.3f} %  "
          f"(PGD {fitted['steps']} Schritte, zulaessig={fitted['feasible']})")
    print(f"{'Index':>6}{'cp':>9}{'p5':>9}{'p95':>9}" + (f"{'Anteil':>9}" if shares else "")
          + "   Beleg")

    for k in range(MAX_INDEX):
        low, high = (percentile(fitted["draws"][k], p) for p in PERCENTILES)
        share = f"{shares[k]:>8.1f}%" if shares else ""
        verdict = "ueber null" if low > 0.5 else "nicht von null zu trennen"
        print(f"{k + 1:>6}{fitted['curve'][k]:>9.1f}{low:>9.1f}{high:>9.1f}{share}   {verdict}")

    return [0] + [int(round(v)) for v in fitted["curve"]]
