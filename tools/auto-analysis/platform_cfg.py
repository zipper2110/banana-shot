"""Platform decisions of the detection chain, in one place (docs/auto-analysis/mac-setup.md).

The chain was made on a Windows PC with an NVIDIA GPU. This module lets the same scripts run on
a PC with no NVIDIA GPU and on a Mac with Apple Silicon. On a PC with an NVIDIA GPU, each
function gives the same value as before this module: the results do not change.

Environment variables (all optional):
  BANANASHOT_LAB      the lab root folder. Default: D:/bananashot-lab on Windows, ~/bananashot-lab on other systems.
                      It contains ext/weights (ball and pose weights), hf (Hugging Face cache), data/models (rally models).
  BANANASHOT_DEVICE   PyTorch device: cuda, mps or cpu. Default: the first that is available, in this sequence.
  BANANASHOT_ORT      ONNX Runtime provider (pose models): cuda, coreml or cpu. Default: the first that is available.
  BANANASHOT_DECODE   video decode: cuda (GPU decode and GPU scale), videotoolbox (Apple decode) or software.
                      Default: cuda when the PyTorch device is cuda, else software.
  BANANASHOT_FFMPEG   the folder of ffmpeg and ffprobe, when they are not on PATH.
"""
import os, shutil, sys

WINDOWS = sys.platform == "win32"
MAC = sys.platform == "darwin"
if MAC:
    # An operation that MPS does not have then runs on the CPU. Set before "import torch".
    os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

_cache = {}


def _env(name, allowed):
    v = os.environ.get(name, "").strip().lower()
    if v and v not in allowed:
        raise RuntimeError(f"{name}={v} is not valid. Use one of: {', '.join(allowed)}.")
    return v


# ---- folders and files

def lab_root():
    v = os.environ.get("BANANASHOT_LAB", "").strip()
    if v:
        return os.path.expanduser(v)
    return "D:/bananashot-lab" if WINDOWS else os.path.join(os.path.expanduser("~"), "bananashot-lab")


def data_root(configured):
    """The folder of the match folders and of "models". "configured" is "labRoot" of matches.json: it applies
    on Windows when BANANASHOT_LAB is not set."""
    if WINDOWS and not os.environ.get("BANANASHOT_LAB", "").strip():
        return configured
    return os.path.join(lab_root(), "data")


def weights_dir():
    return lab_root() + "/ext/weights"


def hf_home():
    return lab_root() + "/hf"


# The Hugging Face library reads HF_HOME when it is imported (timm imports it). So set it here: each script of the
# chain imports this module first. A value from the environment (the app gives one) stays.
os.environ.setdefault("HF_HOME", hf_home())


def pose_model(url):
    """The ONNX file of a pose model: the file in the weights folder if it is there (a copy from the PC), else the
    URL (rtmlib then downloads the file into its cache, ~/.cache/rtmlib)."""
    name = os.path.splitext(os.path.basename(url))[0] + ".onnx"
    local = os.path.join(weights_dir(), name)
    return local if os.path.exists(local) else url


def tool(name):
    """The path of ffmpeg or ffprobe. A Mac app that starts from the Finder has no Homebrew folder on PATH."""
    if name not in _cache:
        own = os.environ.get("BANANASHOT_FFMPEG", "").strip()
        found = shutil.which(name, path=own) if own else None
        if not found and not shutil.which(name) and not WINDOWS:
            found = next((os.path.join(f, name) for f in ("/opt/homebrew/bin", "/usr/local/bin")
                          if os.path.exists(os.path.join(f, name))), None)
        _cache[name] = found or name
    return _cache[name]


# ---- PyTorch

def device():
    """"cuda", "mps" or "cpu"."""
    if "device" not in _cache:
        import torch
        v = _env("BANANASHOT_DEVICE", ("cuda", "mps", "cpu"))
        if not v:
            v = "cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu"
        _cache["device"] = v
    return _cache["device"]


def half():
    """True: the models run with 16-bit floats (only on CUDA, as the lab did). MPS and the CPU use 32-bit floats."""
    return device() == "cuda"


def to_device(x):
    """Moves a model or a tensor to the device."""
    return x.cuda() if device() == "cuda" else x.to(device())


def fp(x):
    """Gives a model or a tensor the float type of the device (see half)."""
    return x.half() if half() else x.float()


def small_device():
    """The device for the small rally model: CUDA as before, else the CPU (it is fast there, and MPS adds a risk)."""
    return "cuda" if device() == "cuda" else "cpu"


def empty_cache():
    import torch
    if device() == "cuda":
        torch.cuda.empty_cache()
    elif device() == "mps":
        torch.mps.empty_cache()


# ---- ONNX Runtime (through rtmlib)

def ort_device():
    """The device name for rtmlib: "cuda", "mps" (= the CoreML provider) or "cpu"."""
    if "ort" not in _cache:
        v = _env("BANANASHOT_ORT", ("cuda", "coreml", "cpu"))
        if not v:
            device()                                   # "import torch" before ONNX Runtime (CUDA libraries)
            import onnxruntime
            have = onnxruntime.get_available_providers()
            v = "cuda" if "CUDAExecutionProvider" in have else "coreml" if "CoreMLExecutionProvider" in have else "cpu"
        _cache["ort"] = {"coreml": "mps"}.get(v, v)
    return _cache["ort"]


# ---- ffmpeg

def decode():
    """"cuda", "videotoolbox" or "software"."""
    if "decode" not in _cache:
        _cache["decode"] = _env("BANANASHOT_DECODE", ("cuda", "videotoolbox", "software")) or ("cuda" if device() == "cuda" else "software")
    return _cache["decode"]


def hwaccel():
    """The ffmpeg input options for a decode that gives frames in system memory."""
    return {"cuda": ["-hwaccel", "cuda"], "videotoolbox": ["-hwaccel", "videotoolbox"], "software": []}[decode()]


def summary():
    return f"platform: torch {device()}{' fp16' if half() else ' fp32'}, onnxruntime {ort_device()}, decode {decode()}, lab {lab_root()}"
