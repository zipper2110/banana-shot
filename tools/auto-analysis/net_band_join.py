"""Item 17 (S2, E119): joins the level-1 files of the parts of the ball pass (net_band_mask.MaskWriter.save) into
the file of the one pass. The format stays the format of net_band_mask.py: frame, prev, off, size, data, box, crop,
scale, thr, version. Owner: S5 (the format). This module is not in the level-1 key (detect.NETMASK_CODE): it is on
the "no effect" list of test_code_closure, because the join gives the same arrays as the one pass
(test_ball_parts.MaskParts checks it). It imports no module of the folder.

  join_parts([(file, lo, hi), ...], path)         after the ball stage in parts (detect.py)
"""
import numpy as np

SAME = ("box", "crop", "scale", "thr", "version")     # the same for each picture of a video


def join_parts(parts, path):
    """parts: [(file, lo, hi)] in the order of the pass; the part owns the frames lo <= frame < hi (hi None: to the
    end). The decode of a part goes on after hi, so the first pictures of the next part (no previous picture in its
    own decode: no record) get their record from the part before. A frame in more than one file gets the record of
    the part that owns it, else the record of the last part before it (the end of a decode window can give other
    pictures, as the fps filter has no next frame there). Writes path with the records in frame order, as
    MaskWriter.save after the one pass, and returns path."""
    zs = [np.load(f) for f, _, _ in parts]
    for k in SAME:
        if any(not np.array_equal(z[k], zs[0][k]) for z in zs):
            raise ValueError(f"net band mask parts: {k} is not the same in each part")
    rows = []                                         # (frame, 0 = owned / 1 = not owned, -part, record)
    for p, (z, (_, lo, hi)) in enumerate(zip(zs, parts)):
        for j, fr in enumerate(z["frame"].tolist()):
            rows.append((fr, 0 if lo <= fr and (hi is None or fr < hi) else 1, -p, j))
    rows.sort()
    frame, prev, size, data, last = [], [], [], [], None
    for fr, _, p, j in rows:
        p = -p
        if fr == last:
            continue
        last, z = fr, zs[p]
        o, s = int(z["off"][j]), int(z["size"][j])
        frame.append(fr)
        prev.append(int(z["prev"][j]))
        size.append(s)
        data.append(z["data"][o:o + s])
    size = np.array(size, np.int64)
    off = np.concatenate([[0], np.cumsum(size)[:-1]]).astype(np.int64) if len(size) else np.zeros(0, np.int64)
    z0 = zs[0]
    np.savez(path, frame=np.array(frame, np.int32), prev=np.array(prev, np.int32), off=off, size=size,
             data=np.concatenate(data) if data else np.zeros(0, np.uint8), box=z0["box"], crop=z0["crop"],
             scale=z0["scale"], thr=z0["thr"], version=z0["version"])
    return path
