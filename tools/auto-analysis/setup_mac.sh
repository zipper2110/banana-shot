#!/bin/bash
# Setup of the automatic point detection on a Mac with Apple Silicon (docs/auto-analysis/mac-setup.md).
# Run this script in the folder tools/auto-analysis of the repository:  bash setup_mac.sh
# Before: copy the folder "bananashot-lab" (made by pack_for_mac.py on the lab PC) to the home folder.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
LAB="${BANANASHOT_LAB:-$HOME/bananashot-lab}"

if [ "$(uname -s)" != "Darwin" ] || [ "$(uname -m)" != "arm64" ]; then
  echo "This script is for a Mac with Apple Silicon (arm64). This computer: $(uname -s) $(uname -m)."
  exit 1
fi

# ffmpeg and ffprobe
PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if ! command -v ffmpeg >/dev/null || ! command -v ffprobe >/dev/null; then
  echo "ffmpeg is not installed. Install it:  brew install ffmpeg   Then start this script again."
  exit 1
fi
echo "ffmpeg: $(ffmpeg -version | head -1)"
echo "The lab PC has ffmpeg 8.1. Another main version can give other pictures: selfcheck.py shows it."

# Python 3.13 (the version of the lab venv)
PY="$(command -v python3.13 || true)"
if [ -z "$PY" ]; then
  echo "Python 3.13 is not installed. Install it:  brew install python@3.13   Then start this script again."
  exit 1
fi

# the weights and models from the lab PC
for f in ext/weights/wasb_tennis.pth.tar data/models/rally_ball_t2.pt data/models/rally_base_t2.pt hf/hub; do
  if [ ! -e "$LAB/$f" ]; then
    echo "Missing: $LAB/$f"
    echo "Copy the folder bananashot-lab from the lab PC (pack_for_mac.py) to $LAB first."
    exit 1
  fi
done

# venv and packages
if [ ! -x "$LAB/venv/bin/python" ]; then
  "$PY" -m venv "$LAB/venv"
fi
"$LAB/venv/bin/python" -m pip install --upgrade pip
"$LAB/venv/bin/python" -m pip install -r "$HERE/requirements-mac.txt"
mkdir -p "$LAB/app"

echo
echo "Setup complete. Next steps:"
echo "1. Run the self-check (some minutes):"
echo "     cd \"$HERE\" && \"$LAB/venv/bin/python\" selfcheck.py check"
echo "2. Write this text into the file \"$HOME/Library/Application Support/BananaShot/autodetect.json\":"
cat <<EOF
{
  "python": "$LAB/venv/bin/python",
  "script": "$HERE/detect.py",
  "workRoot": "$LAB/app",
  "environment": { "BANANASHOT_LAB": "$LAB" }
}
EOF
