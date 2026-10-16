import json, sys, statistics as st
a = json.load(open(sys.argv[1] + '/edl.json'))['points']
h = json.load(open(sys.argv[2] + '/edl.json'))['points']
A = sorted((p['startMs'], p['endMs']) for p in a); H = sorted((p['startMs'], p['endMs']) for p in h)
def ov(x, y): return max(0, min(x[1], y[1]) - max(x[0], y[0]))
match = {}; used = set()
for i, hp in enumerate(H):
    best = max(range(len(A)), key=lambda j: ov(hp, A[j]))
    o = ov(hp, A[best])
    if o >= 0.5 * min(hp[1]-hp[0], A[best][1]-A[best][0]) and best not in used:
        match[i] = best; used.add(best)
ds = [(A[j][0]-H[i][0])/1000 for i, j in match.items()]; de = [(A[j][1]-H[i][1])/1000 for i, j in match.items()]
def q(v, p): v = sorted(v); return v[min(len(v)-1, int(p*len(v)))]
print('hand', len(H), 'claude', len(A), 'matched', len(match))
print('recall %.1f%%  precision %.1f%%' % (100*len(match)/len(H), 100*len(match)/len(A)))
print('start err s: median %.1f  |median| %.1f  p90|.| %.1f' % (st.median(ds), st.median(map(abs, ds)), q(list(map(abs, ds)), .9)))
print('end   err s: median %.1f  |median| %.1f  p90|.| %.1f' % (st.median(de), st.median(map(abs, de)), q(list(map(abs, de)), .9)))
f = lambda ms: '%d:%04.1f' % (ms//60000, ms%60000/1000)
miss = [H[i] for i in range(len(H)) if i not in match]; extra = [A[j] for j in range(len(A)) if j not in used]
print('missed', len(miss), ' '.join(f(s) for s, e in miss))
print('extra', len(extra), ' '.join(f(s) for s, e in extra))
# missed/extra by 10-min bucket
import collections
for name, L in (('missed', miss), ('extra', extra)):
    c = collections.Counter(s//600000*10 for s, e in L); print(name, 'per 10 min:', dict(sorted(c.items())))
print('hand first', f(H[0][0]), 'last', f(H[-1][1]), '; claude first', f(A[0][0]), 'last', f(A[-1][1]))
gaps = [(f(H[k][1]), round((H[k+1][0]-H[k][1])/1000)) for k in range(len(H)-1) if H[k+1][0]-H[k][1] > 120000]
print('hand gaps > 2 min:', gaps)
