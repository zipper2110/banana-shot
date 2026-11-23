"""E26: reference points for the court model, and the accuracy measure.

The reference file court_ref.json holds, for some time windows of each video, points that a person marked by eye
on zoomed crops of the window picture (the median of some keyframes: the players go away). Each point is on the
centre of a painted court line, or on the crossing of two lines.

Commands:
  python court_ref.py med                      write the window pictures (D:/bananashot-lab/e26/med)
  python court_ref.py sheet <video> <t0> [track file]   zoomed crops at the model crossings, with a px grid
  python court_ref.py crop <video> <t0> <x> <y> [half] [zoom]   one zoomed crop
  python court_ref.py build                    write court_ref.json from the snaps and the hand marks (court_ref_marks.json)
  python court_ref.py eval [track file name]   error of a court track against court_ref.json, for each video and line
  python court_ref.py refine                   refine the reference windows (court_refine.py) and measure
  python court_ref.py evalr                    the same measure for court_track_refined.json (half models)
  python court_ref.py shift                    how far the kink points of fullflights_s0.35.json move on the court

How a reference point is made. "snap": the two painted lines near the crossing are found in the picture (bar
centre across the line at many places, robust line fit), the point is the crossing of the two centre lines, and a
person checked the point on a zoomed crop. "hand": a person read the point from a zoomed crop with a px grid (the
snap fails there: glare, a line with no contrast to the ground outside, a line behind the net mesh). The accuracy
is about 0.3 px for "snap" and 1 to 1.5 px for "hand". A line point ("line:<name>") is a hand mark on the centre of
one line only. court_ref_marks.json holds the hand marks and the points that are not visible ("x").
"""
import json, os, sys
import numpy as np
import cv2
import court_auto
from court_fit import SW, DW, BL, SL

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = "D:/bananashot-lab/e26"
FOLDERS = {"m1015": "D:/bananashot-lab/data/m1015", "m1020": "D:/bananashot-lab/data/m1020",
           "m1021": "D:/bananashot-lab/data/m1021", "m1030": "D:/bananashot-lab/data/m1030",
           "m1001": "D:/bananashot-lab/data/m1001", "m1016": "D:/bananashot-lab/data/m1016",
           "m0913": "D:/bananashot-lab/app/m0913"}
WINDOWS = {"m1015": [1080, 1980, 3300], "m1020": [960, 3840], "m1021": [600, 3240, 5760], "m1030": [900, 2700, 4500],
           "m1001": [1140, 4560], "m1016": [1260, 5040], "m0913": [960, 2880, 4800]}
# the court lines: name -> (end 1, end 2) in court meters (outer edge of the paint, as in the rules)
LINES = {"near_base": ((-DW, -BL), (DW, -BL)), "far_base": ((-DW, BL), (DW, BL)),
         "near_serv": ((-SW, -SL), (SW, -SL)), "far_serv": ((-SW, SL), (SW, SL)),
         "center": ((0, -SL), (0, SL)),
         "left_dbl": ((-DW, -BL), (-DW, BL)), "right_dbl": ((DW, -BL), (DW, BL)),
         "left_sgl": ((-SW, -BL), (-SW, BL)), "right_sgl": ((SW, -BL), (SW, BL))}
# the line crossings: name -> (court x, court y)
CROSS = {}
for yn, y in (("nb", -BL), ("ns", -SL), ("fs", SL), ("fb", BL)):
    for xn, x in (("ld", -DW), ("ls", -SW), ("c", 0.0), ("rs", SW), ("rd", DW)):
        if yn in ("ns", "fs") and xn in ("ld", "rd"):
            continue
        if yn in ("nb", "fb") and xn == "c":
            continue
        CROSS[f"{yn}_{xn}"] = (x, y)


def median_frame(folder, t0, t1, n=15):
    ts = np.linspace(t0 + 1, t1 - 1, n).astype(int)
    imgs = [cv2.imread(os.path.join(folder, "kf", f"{t + 1:05d}.jpg")) for t in ts]
    imgs = [i for i in imgs if i is not None]
    return np.median(np.stack(imgs), 0).astype(np.uint8)


def med_path(vid, t0):
    return f"{OUT}/med/{vid}_{t0:05d}.png"


def window(vid, t0, name="court_track.json"):
    for w in json.load(open(os.path.join(FOLDERS[vid], name))):
        if w["t0"] == t0:
            return w
    raise KeyError((vid, t0))


def picture(vid, t0):
    p = med_path(vid, t0)
    if not os.path.exists(p):
        w = window(vid, t0)
        cv2.imwrite(p, median_frame(FOLDERS[vid], w["t0"], w["t1"]))
    return cv2.imread(p)


def crop(img, cx, cy, half=24, zoom=8, marks=(), lines=()):
    """Zoomed crop around (cx, cy) with a px grid. A tick is at each 5 px, with the px value at each 10 px.
    The centre of the source px (x, y) is at the zoomed place ((x - x0 + 0.5) * zoom)."""
    x0, y0 = int(round(cx)) - half, int(round(cy)) - half
    n = 2 * half
    M = np.float32([[zoom, 0, -x0 * zoom + (zoom - 1) / 2], [0, zoom, -y0 * zoom + (zoom - 1) / 2]])
    out = cv2.warpAffine(img, M, (n * zoom, n * zoom), flags=cv2.INTER_NEAREST, borderValue=(60, 0, 60))

    def z(x, y):
        return int(round((x - x0 + 0.5) * zoom)), int(round((y - y0 + 0.5) * zoom))
    for a, b, col in lines:
        cv2.line(out, z(*a), z(*b), col, 1, cv2.LINE_AA)
    for v in range(x0 - x0 % 5 + 5, x0 + n, 5):
        px = z(v, 0)[0]
        cv2.line(out, (px, 0), (px, 6 if v % 10 else 12), (0, 255, 255), 1)
        cv2.line(out, (px, n * zoom - 1), (px, n * zoom - (6 if v % 10 else 12)), (0, 255, 255), 1)
        if v % 10 == 0:
            cv2.putText(out, str(v), (px - 14, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 255, 255), 1, cv2.LINE_AA)
    for v in range(y0 - y0 % 5 + 5, y0 + n, 5):
        py = z(0, v)[1]
        cv2.line(out, (0, py), (6 if v % 10 else 12, py), (0, 255, 255), 1)
        cv2.line(out, (n * zoom - 1, py), (n * zoom - (6 if v % 10 else 12), py), (0, 255, 255), 1)
        if v % 10 == 0:
            cv2.putText(out, str(v), (14, py + 4), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (0, 255, 255), 1, cv2.LINE_AA)
    for x, y, col in marks:
        p = z(x, y)
        cv2.line(out, (p[0] - 9, p[1]), (p[0] - 3, p[1]), col, 1)
        cv2.line(out, (p[0] + 3, p[1]), (p[0] + 9, p[1]), col, 1)
        cv2.line(out, (p[0], p[1] - 9), (p[0], p[1] - 3), col, 1)
        cv2.line(out, (p[0], p[1] + 3), (p[0], p[1] + 9), col, 1)
    return out


def sheet(vid, t0, points, half=24, zoom=8, cols=4, model=None, marks=None, out=None):
    """One picture with a zoomed crop for each named point: {name: (x, y)}. model: H, drawn as thin cyan lines.
    marks: {name: [(x, y), ...]} extra marks (red)."""
    img = picture(vid, t0)
    lines = []
    if model is not None:
        for a, b in LINES.values():
            pa, pb = court_auto.to_img(model, [a, b])
            lines.append((pa, pb, (255, 255, 0)))
    tiles = []
    for name, (x, y) in points.items():
        mk = [(mx, my, (0, 0, 255)) for mx, my in (marks or {}).get(name, [])]
        t = crop(img, x, y, half, zoom, mk, lines)
        cv2.putText(t, name, (30, 2 * half * zoom - 16), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2, cv2.LINE_AA)
        tiles.append(cv2.copyMakeBorder(t, 2, 2, 2, 2, cv2.BORDER_CONSTANT, value=(255, 255, 255)))
    while len(tiles) % cols:
        tiles.append(np.zeros_like(tiles[0]))
    pic = np.vstack([np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)])
    out = out or f"{OUT}/sheets/{vid}_{t0:05d}.png"
    cv2.imwrite(out, pic)
    return out


def model_points(vid, t0, name="court_track.json", w=1920, h=1080, margin=6):
    """The crossings of the model that are inside the frame: {name: (x, y)}."""
    H = np.array(window(vid, t0, name)["H"])
    res = {}
    for k, (x, y) in CROSS.items():
        p = court_auto.to_img(H, [(x, y)])[0]
        if margin < p[0] < w - margin and margin < p[1] < h - margin:
            res[k] = (float(p[0]), float(p[1]))
    return H, res


# ---------------------------------------------------------------- local snap of a crossing

def bar(gray, c, d, R=14, avg=2, min_con=12):
    """The bright bar (painted line) across the direction d at the place c. Returns (offset of the bar centre
    along the normal in px, bar width px, contrast) or None. The bar edges are at half height between the peak
    and the darker level on each side."""
    n = np.array([-d[1], d[0]])
    offs = np.arange(-R, R + 0.01, 0.5)
    prof = np.zeros(len(offs), np.float32)
    for a in range(-avg, avg + 1):
        xs = (c[0] + a * d[0] + offs * n[0]).astype(np.float32)[None]
        ys = (c[1] + a * d[1] + offs * n[1]).astype(np.float32)[None]
        prof += cv2.remap(gray, xs, ys, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)[0]
    prof /= 2 * avg + 1
    sm = np.convolve(prof, np.ones(3) / 3, "same")
    best = None
    for h in (2, 3, 4, 6, 8, 10):                         # h: half of the tested bar width, in samples of 0.5 px
        hs = 2 * h
        if 2 * hs + 1 >= len(sm):
            break
        r = np.minimum(sm[hs:-hs] - sm[:-2 * hs], sm[hs:-hs] - sm[2 * hs:])
        k = int(r.argmax())
        if best is None or r[k] > best[0] * 1.15:
            best = (float(r[k]), k + hs, hs)
    con, k, hs = best
    if con < min_con:
        return None
    a, b = max(0, k - 2 * hs), min(len(sm) - 1, k + 2 * hs)
    k = a + int(sm[a:b + 1].argmax())
    lo_l, lo_r = sm[max(0, k - 2 * hs):k + 1].min(), sm[k:k + 2 * hs + 1].min()
    edges = []
    for side, lo in ((-1, lo_l), (1, lo_r)):
        half = (sm[k] + lo) / 2
        i = k
        while 0 < i < len(sm) - 1 and sm[i] > half:
            i += side
        if sm[i] > half:
            return None
        j = i - side
        t = (sm[j] - half) / max(1e-6, sm[j] - sm[i])
        edges.append(offs[j] + side * 0.5 * t)
    return (edges[0] + edges[1]) / 2, edges[1] - edges[0], con


def snap_line(gray, p0, d, R=14, reach=60, skip=7, wmax=14):
    """Fit the centre line of the painted line that goes through about p0 with about the direction d.
    Pass 1: bar centres in a wide search range, and the line with the most bar centres within 1.2 px (all point
    pairs are tested). Pass 2: bar centres within 4 px of that line, least squares.
    Returns (point, direction, count of stations, median width px) or None."""
    d = np.asarray(d, float) / np.linalg.norm(d)
    p0 = np.asarray(p0, float)
    line = None
    for rng in (R, 6):
        n = np.array([-d[1], d[0]])
        pts, ws = [], []
        for s in np.r_[np.arange(-reach, -skip + 1, 3), np.arange(skip, reach + 1, 3)]:
            c = p0 + s * d
            if not (rng + 3 < c[0] < gray.shape[1] - rng - 3 and rng + 3 < c[1] < gray.shape[0] - rng - 3):
                continue
            b = bar(gray, c, d, rng)
            if b is not None and b[1] < wmax:
                pts.append(c + b[0] * n)
                ws.append(b[1])
        if len(pts) < 5:
            return None
        P, ws = np.array(pts), np.array(ws)
        if rng == R:
            best = None
            for i in range(len(P)):
                for j in range(i + 4, len(P)):
                    e = P[j] - P[i]
                    e /= np.linalg.norm(e)
                    if abs(e @ d) < np.cos(np.radians(8)):
                        continue
                    r = np.abs((P - P[i]) @ np.array([-e[1], e[0]]))
                    k = r < 1.2
                    sc = k.sum() - 0.02 * np.abs((P[k] - p0) @ n).mean()
                    if best is None or sc > best[0]:
                        best = (sc, k)
            if best is None or best[1].sum() < 5:
                return None
            keep = best[1]
        else:
            m = P.mean(0)
            keep = np.abs((P - p0) @ n) < 2.0
            if keep.sum() < 5:
                return None
        m = P[keep].mean(0)
        _, _, vt = np.linalg.svd(P[keep] - m)
        e = vt[0] if vt[0] @ d > 0 else -vt[0]
        line = (m, e, int(keep.sum()), float(np.median(ws[keep])))
        p0, d = m + ((p0 - m) @ e) * e, e                 # the next pass searches around this line
    return line


def cross_dirs(H, name):
    """Image directions of the two model lines at the model crossing."""
    x, y = CROSS[name]
    p, px, py = court_auto.to_img(H, [(x, y), (x + 0.5, y), (x, y + 0.5)])
    return p, px - p, py - p


def snap_cross(gray, H, name, seed=None, R=14):
    """Snap one crossing: the two line centres near the seed (default: the model crossing), then their crossing.
    Returns dict or None."""
    p, dx, dy = cross_dirs(H, name)
    p0 = np.asarray(seed, float) if seed is not None else p
    la = snap_line(gray, p0, dx, R)
    lb = snap_line(gray, p0, dy, R)
    if la is None or lb is None:
        return None
    q = court_auto.intersect(la[:2], lb[:2])
    if q is None:
        return None
    return {"p": [round(float(q[0]), 2), round(float(q[1]), 2)], "n": [la[2], lb[2]], "w": [round(la[3], 1), round(lb[3], 1)]}



# ---------------------------------------------------------------- reference file and accuracy measure

MARKS = os.path.join(HERE, "court_ref_marks.json")
REF = os.path.join(HERE, "court_ref.json")
ROW = {"nb": "near_base", "ns": "near_serv", "fs": "far_serv", "fb": "far_base"}
COL = {"ld": "left_dbl", "ls": "left_sgl", "c": "center", "rs": "right_sgl", "rd": "right_dbl"}
LINE_ORDER = ["near_base", "near_serv", "center", "left_sgl", "right_sgl", "left_dbl", "right_dbl", "far_serv", "far_base"]
PAINT = 0.05          # m, assumed width of the paint. The rules measure the court to the outer edge of each line


def centre_coord(line, paint=PAINT):
    """The court coordinate of the paint centre of a line: ("x" or "y", value in m)."""
    (x1, y1), (x2, y2) = LINES[line]
    if line == "center":
        return "x", 0.0
    if x1 == x2:
        return "x", x1 - np.sign(x1) * paint / 2
    return "y", y1 - np.sign(y1) * paint / 2


def build():
    """Snap all crossings of the reference windows, apply the hand marks, write court_ref.json."""
    marks = json.load(open(MARKS))
    ref = {}
    for vid, ts in WINDOWS.items():
        for t0 in ts:
            H, pts = model_points(vid, t0)
            gray = cv2.cvtColor(picture(vid, t0), cv2.COLOR_BGR2GRAY)
            mk = marks.get(f"{vid}_{t0}", {})
            points, lpts, hidden = {}, {}, []
            for k in CROSS:
                sd = mk.get(k)
                if sd == "x":
                    hidden.append(k)
                elif sd and len(sd) > 3:
                    points[k] = [sd[0], sd[1], "hand"]
                elif k in pts:
                    r = snap_cross(gray, H, k, sd[:2] if sd else None, sd[2] if sd else 14)
                    if r:
                        points[k] = [r["p"][0], r["p"][1], "snap"]
            for k, v in mk.items():
                if k.startswith("line:"):
                    lpts[k[5:]] = v
            ref[f"{vid}_{t0}"] = {"points": points, "line_points": lpts, "not_visible": hidden}
    json.dump(ref, open(REF, "w"), indent=1)
    print("windows", len(ref), "points", sum(len(r["points"]) for r in ref.values()))


def line_errors(H, Hfar_or_r, r=None):
    if r is None:
        return _line_errors(H, Hfar_or_r)
    return _line_errors(H, r, Hfar_or_r)


def _line_errors(H, r, Hfar=None):
    """Errors of one model against the reference points of one window: list of (line, px, m, kind).
    px: signed distance in the picture from the reference point to the paint centre line of the model.
    m: the same error on the court: the model coordinate of the reference point minus the coordinate of the line."""
    out = []
    Hnear = H
    obs = [(ROW[k.split("_")[0]], p) for k, p in r["points"].items()] + [(COL[k.split("_")[1]], p) for k, p in r["points"].items()]
    obs += [(ln, [q[0], q[1], "hand"]) for ln, v in r["line_points"].items() for q in v]
    for ln, p in obs:
        ax, val = centre_coord(ln)
        (x1, y1), (x2, y2) = LINES[ln]
        a, b = ((val, -40.0), (val, 40.0)) if ax == "x" else ((-40.0, val), (40.0, val))
        H = Hnear
        if Hfar is not None and cv2.perspectiveTransform(np.array([[p[:2]]], np.float32), Hnear)[0, 0, 1] > 0:
            H = Hfar                                       # a model with two planes: the far half has its own H
        ia, ib = court_auto.to_img(H, [a, b]).astype(float)
        d = (ib - ia) / np.linalg.norm(ib - ia)
        px = float((np.array(p[:2], float) - ia) @ np.array([-d[1], d[0]]))
        c = cv2.perspectiveTransform(np.array([[p[:2]]], np.float32), H)[0, 0]
        m = float((c[0] if ax == "x" else c[1]) - val)
        out.append((ln, px, m, p[2]))
    return out


def evaluate(name="court_track.json", quiet=False):
    """Prints, for each video and line, the mean absolute error in px and in m. Returns {video: {line: (px, m, n)}}.
    name: a track file name in the video folders, or a dict {(video, t0): H}."""
    ref = json.load(open(REF))
    res, allrows = {}, []
    for vid, ts in WINDOWS.items():
        rows = []
        for t0 in ts:
            Hm = name[(vid, t0)] if isinstance(name, dict) else np.array(window(vid, t0, name)["H"])
            rows += line_errors(*Hm, ref[f"{vid}_{t0}"]) if isinstance(Hm, tuple) else line_errors(Hm, ref[f"{vid}_{t0}"])
        res[vid] = {}
        for ln in LINE_ORDER:
            e = [(abs(px), abs(m)) for l, px, m, _ in rows if l == ln]
            if e:
                res[vid][ln] = (float(np.mean([v[0] for v in e])), float(np.mean([v[1] for v in e])), len(e))
        allrows += rows
        if not quiet:
            print(f"{vid}: " + "  ".join(f"{ln} {v[0]:.1f}px/{v[1] * 100:.0f}cm({v[2]})" for ln, v in res[vid].items()))
            print(f"   all: mean {np.mean([abs(r[1]) for r in rows]):.2f} px, {np.mean([abs(r[2]) for r in rows]) * 100:.1f} cm; "
                  f"max {max(abs(r[1]) for r in rows):.1f} px, {max(abs(r[2]) for r in rows) * 100:.0f} cm; not visible: "
                  + ", ".join(sorted({k for t0 in ts for k in ref[f'{vid}_{t0}']['not_visible']})))
    if not quiet:
        print(f"ALL: mean {np.mean([abs(r[1]) for r in allrows]):.2f} px, {np.mean([abs(r[2]) for r in allrows]) * 100:.1f} cm, n {len(allrows)}")
    return res


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "med":
        for vid, ts in WINDOWS.items():
            for t0 in ts:
                w = window(vid, t0)
                im = picture(vid, t0)
                H = np.array(w["H"])
                cv2.imwrite(f"{OUT}/med/{vid}_{t0:05d}_model.jpg", court_auto.draw(im, H, (255, 255, 0), 1), [cv2.IMWRITE_JPEG_QUALITY, 85])
                print(vid, t0, w["ok"], round(w["cost"], 2), flush=True)
    elif cmd == "sheet":
        vid, t0 = sys.argv[2], int(sys.argv[3])
        name = sys.argv[4] if len(sys.argv) > 4 and not sys.argv[4].startswith("--") else "court_track.json"
        H, pts = model_points(vid, t0, name)
        gray = cv2.cvtColor(picture(vid, t0), cv2.COLOR_BGR2GRAY)
        seeds = json.load(open(MARKS)).get(f"{vid}_{t0}", {}) if os.path.exists(MARKS) else {}
        snaps, marks, centers = {}, {}, {}
        for k, p in pts.items():
            sd = seeds.get(k)
            if sd == "x" or k.startswith("line:"):                                  # not visible
                continue
            r = snap_cross(gray, H, k, sd[:2] if sd else None, (sd[2] if sd and len(sd) > 2 else 5) if sd else 14)
            if sd and len(sd) > 3:                         # a pure hand mark: no snap
                r = {"p": sd[:2], "n": [0, 0], "w": [0, 0]}
            snaps[k] = r
            marks[k] = [r["p"]] if r else []
            centers[k] = r["p"] if r else p
        print(sheet(vid, t0, centers, half=20, zoom=7, cols=5, model=H if "--model" in sys.argv else None, marks=marks))
        for k in pts:
            print(k, "model", [round(v, 1) for v in pts[k]], "snap", snaps.get(k))
        json.dump(snaps, open(f"{OUT}/sheets/{vid}_{t0:05d}_snap.json", "w"))
    elif cmd == "build":
        build()
    elif cmd == "eval":
        evaluate(sys.argv[2] if len(sys.argv) > 2 else "court_track.json")
    elif cmd == "refine":                                 # refine the reference windows and measure: [mode] [f]
        import court_refine
        mode = sys.argv[2] if len(sys.argv) > 2 else "H"
        models = {}
        for vid, ts in WINDOWS.items():
            for t0 in ts:
                H, q = court_refine.refine(picture(vid, t0), np.array(window(vid, t0)["H"]), mode)
                models[(vid, t0)] = H
                print(vid, t0, {k: v for k, v in q.items() if k not in ("line_n", "line_rms")}, flush=True)
                print("    n", q.get("line_n"), "rms", q.get("line_rms"), flush=True)
        print("---- one plane")
        evaluate(models)
        json.dump({f"{k[0]}_{k[1]}": v.tolist() for k, v in models.items()}, open(f"{OUT}/refined_{mode}.json", "w"))
        two = {}
        for (vid, t0), H in models.items():
            hv = court_refine.refine_halves(picture(vid, t0), H)
            two[(vid, t0)] = (hv[0], hv[1]) if hv else H
            print(vid, t0, hv[2] if hv else None, flush=True)
        print("---- two planes")
        evaluate(two)
        print("---- one plane nearest to the two planes")
        evaluate({k: (court_refine.blend(*v) if isinstance(v, tuple) else v) for k, v in two.items()})
        json.dump({f"{k[0]}_{k[1]}": [h.tolist() for h in v] for k, v in two.items() if isinstance(v, tuple)}, open(f"{OUT}/refined_two.json", "w"))
    elif cmd == "evalr":
        models = {}
        for vid, ts in WINDOWS.items():
            for t0 in ts:
                w = window(vid, t0, "court_track_refined.json")
                models[(vid, t0)] = (np.array(w["H_near"]), np.array(w["H_far"])) if "H_near" in w else np.array(w["H"])
        print("---- half models (to_court)")
        evaluate(models)
        print("---- the one model H of the refined file")
        evaluate("court_track_refined.json")
    elif cmd == "shift":
        import court_refine
        for vid, folder in FOLDERS.items():
            fp = os.path.join(folder, "fullflights_s0.35.json")
            if not os.path.exists(fp) or not os.path.exists(os.path.join(folder, "court_track_refined.json")):
                continue
            kk = np.array(json.load(open(fp))["kinks"])[:, :3]
            old = [w for w in json.load(open(os.path.join(folder, "court_track.json"))) if w["ok"]]
            new = {w["t0"]: w for w in json.load(open(os.path.join(folder, "court_track_refined.json")))}
            rows = []
            for t, x, y in kk:
                w = min(old, key=lambda w: 0 if w["t0"] <= t < w["t1"] else min(abs(t - w["t0"]), abs(t - w["t1"])))
                a = cv2.perspectiveTransform(np.array([[[x, y]]], np.float64), np.array(w["H"]))[0, 0]
                b = court_refine.to_court(new[w["t0"]], [[x, y]])[0]
                if abs(a[0]) < DW + 1 and abs(a[1]) < BL + 2:
                    rows.append((a[0], a[1], b[0] - a[0], b[1] - a[1]))
            r = np.array(rows)
            out = []
            for name, m in (("near end (y < -6.4)", r[:, 1] < -SL), ("near service area", (r[:, 1] >= -SL) & (r[:, 1] < 0)),
                            ("far service area", (r[:, 1] >= 0) & (r[:, 1] < SL)), ("far end (y > 6.4)", r[:, 1] >= SL)):
                if m.sum() > 5:
                    d = np.hypot(r[m, 2], r[m, 3])
                    out.append(f"{name}: n {m.sum()}, median {np.median(d) * 100:.0f} cm, p90 {np.percentile(d, 90) * 100:.0f} cm, "
                               f"mean dx {r[m, 2].mean() * 100:+.0f} dy {r[m, 3].mean() * 100:+.0f} cm")
            print(vid, "kinks on the court", len(r), "| " + " | ".join(out), flush=True)
    elif cmd == "crop":
        vid, t0, x, y = sys.argv[2], int(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5])
        half = int(sys.argv[6]) if len(sys.argv) > 6 else 24
        zoom = int(sys.argv[7]) if len(sys.argv) > 7 else 8
        p = f"{OUT}/sheets/crop_{vid}_{t0}_{int(x)}_{int(y)}.png"
        cv2.imwrite(p, crop(picture(vid, t0), x, y, half, zoom))
        print(p)
