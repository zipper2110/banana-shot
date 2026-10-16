import json, os, re, sys, uuid, datetime
pts_file, out_root, name, hand_dir = sys.argv[1:5]
pat = re.compile(r'^(\d+):(\d+\.\d) (\d+):(\d+\.\d)')
pts = []
for line in open(pts_file, encoding='utf-8'):
    m = pat.match(line)
    if m:
        s = (int(m[1]) * 60 + float(m[2])) * 1000
        e = (int(m[3]) * 60 + float(m[4])) * 1000
        assert e > s, line
        pts.append((round(s), round(e)))
pts.sort()
for a, b in zip(pts, pts[1:]):
    assert a[1] <= b[0], (a, b)
d = os.path.join(out_root, name)
os.makedirs(d, exist_ok=False)
now = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%fZ')
proj = {"id": str(uuid.uuid4()), "name": name, "createdAt": now, "lastOpenedAt": now,
        "version": 1, "sourceVideo": "D:\PXL_20261001_130801998.mp4"}
ids = [str(uuid.uuid4()) for _ in pts]
edl = {"points": [{"id": i, "startMs": s, "endMs": e, "favorite": False} for i, (s, e) in zip(ids, pts)],
       "version": 1, "comments": [], "commentDefaults": {"colorHex": "#D9D900"}, "nextCommentId": 1}
hand = json.load(open(os.path.join(hand_dir, 'score.json'), encoding='utf-8'))
score = {"outcomes": {i: "NONE" for i in ids}, "version": 1,
         "player1Name": hand["player1Name"], "player2Name": hand["player2Name"],
         "player1ColorHex": hand["player1ColorHex"], "player2ColorHex": hand["player2ColorHex"],
         "scoreboard": hand["scoreboard"], "rules": hand["rules"],
         "manualGameWins": {}, "manualSetWins": {}, "scoreSettingsReviewed": True,
         "serverMarks": {}, "sport": "TENNIS", "useDefaultScoreboard": False}
adj = {"brightness": 1.0, "contrast": 1.0, "saturation": 1.0, "shadows": 0.0, "highlights": 0.0, "version": 1}
def w(fn, obj):
    with open(os.path.join(d, fn), 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
w(name + '.trproj', proj); w('edl.json', edl); w('score.json', score); w('adjustments.json', adj)
print('wrote', len(pts), 'points to', d)
