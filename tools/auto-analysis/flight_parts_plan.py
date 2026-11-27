"""A45 (S2, E204): a dry run of the flights stage of detect.py on work folders. It writes nothing.

For each folder it lists the net band level 2 (runs again or not), the adoption of the one flights key of before A45
(adopt_flight_parts), and the parts of the flights stage that a detect.py run of this code would run
(flight_parts_to_run). The ball tag is the tag of the recorded netmask key (the run computes the same tag when the
ball file and the settings are the same). The court track setting: the default "" (or the value in result.json).
Usage: python flight_parts_plan.py <work folder> ..."""
import json, os, sys
import detect


def plan(work):
    keys = json.load(open(os.path.join(work, "stage_keys.json")))
    detect.STAGE_KEYS.clear()
    detect.STAGE_KEYS.update(keys)
    detect.KEYS_FILE = None                                   # nothing is written
    mask = keys.get("netmask")
    if not mask:
        return {"why": "no netmask key: the ball tag is not known"}
    tag = mask[:-9]
    court_track = ""
    try:
        court_track = json.load(open(os.path.join(work, "result.json"))).get("settings", {}).get("courtTrack") or ""
    except (OSError, ValueError, AttributeError):
        pass
    nb_now = mask + "_" + detect.code_key("netband")
    netband = "current" if keys.get("netband") == nb_now else "runs again"
    court = [os.path.join(work, f) for f in ("court_track.json", "court_track_refined.json")]
    adopted = detect.adopt_flight_parts(work, tag, court_track, court, nb_now)
    runs = detect.flight_parts_to_run(work, detect.flight_keys(tag, court_track, court, nb_now))
    return {"netband": netband, "adoptOneKey": adopted, "flightPartsRun": runs}


if __name__ == "__main__":
    for w in sys.argv[1:]:
        print(w, json.dumps(plan(w)))
