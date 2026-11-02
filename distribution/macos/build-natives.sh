#!/usr/bin/env bash
# Builds the pinned, self-contained macOS native files of BananaShot (B-13, step 4).
#
# Output (target/native/macos-arm64):
#   mpv/libmpv.2.dylib     LGPL libmpv. FFmpeg (LGPL), libplacebo, libass, and their
#                          dependencies are linked statically into it.
#   mpv/libmpv.dylib       A link to libmpv.2.dylib. JNA loads libmpv with the name "mpv".
#   mpv/libMoltenVK.dylib  The Vulkan driver of mpv (vo=gpu-next, gpu-api=vulkan).
#   ffmpeg/bin/ffmpeg      GPL FFmpeg with libx264, libass, and VideoToolbox. The export
#   ffmpeg/bin/ffprobe     runs these programs as separate processes.
#   LICENSES/, BUILD-INFO.txt
#
# The app is not GPL (Elastic License 2.0). libmpv runs in the process of the app, so libmpv and
# all the code in it must be LGPL or more permissive (mpv -Dgpl=false, FFmpeg without --enable-gpl).
# Only the ffmpeg and ffprobe programs contain GPL code (x264).
#
# Each source has a pinned SHA-256 or a pinned Git commit. The build target is macOS 15 on
# Apple Silicon (arm64). The only build tools from Homebrew are meson, ninja, cmake, and pkgconf.
# They are not in the output. The script refuses to link a library of Homebrew.
#
# Needs: Xcode Command Line Tools (clang, swiftc), and brew install meson ninja cmake pkgconf
# Usage: distribution/macos/build-natives.sh [--clean]
set -euo pipefail

DEPLOYMENT_TARGET="15.0"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$ROOT/target/native-build/macos"
SRC="$WORK/src"
BUILD="$WORK/build"
STAMPS="$WORK/stamps"
# Static libraries that libmpv and ffmpeg both use (no GPL code).
DEPS="$WORK/prefix-deps"
# The LGPL FFmpeg libraries for libmpv.
LGPL="$WORK/prefix-lgpl"
# x264 and the GPL FFmpeg programs.
GPL="$WORK/prefix-gpl"
OUT="$ROOT/target/native/macos-arm64"

# --- Pinned sources ----------------------------------------------------------------------------
# name|version|URL|SHA-256|license
TARBALLS=(
  "freetype|2.15.0|https://downloads.sourceforge.net/project/freetype/freetype2/2.15.0/freetype-2.15.0.tar.xz|93cd47eb719c325107f2d3cf087d06299e944c3b48a40b55537302304dea1dd5|FTL"
  "fribidi|1.0.18|https://github.com/fribidi/fribidi/releases/download/v1.0.18/fribidi-1.0.18.tar.xz|1de5e8073d035388b71d0b2d452b1da1f8caeea8a7702997b401fe1b6250727f|LGPL-2.1-or-later"
  "harfbuzz|15.0.0|https://github.com/harfbuzz/harfbuzz/releases/download/15.0.0/harfbuzz-15.0.0.tar.xz|968536e9abd782f07f7954044c0c4020199fcdd39b41d221ec42d2a3be4a44df|MIT"
  "libass|0.17.5|https://github.com/libass/libass/releases/download/0.17.5/libass-0.17.5.tar.xz|2dca25c0e0c837ddf00b52011b3f82cac1e4ddd3ad018227806b0c2288864acc|ISC"
  "lcms2|2.19.1|https://downloads.sourceforge.net/project/lcms/lcms/2.19.1/lcms2-2.19.1.tar.gz|bfc54f7bab59fbc921012014a8032e4cba4abd46db47d46b76416a8c0b2815c8|MIT"
  "glslang|16.6.0|https://github.com/KhronosGroup/glslang/archive/refs/tags/16.6.0.tar.gz|9c09b901149c729df745057dafa815278aaa101b84d2b6e14f16a42de52f97f2|BSD-3-Clause AND Apache-2.0 AND MIT AND GPL-3.0-or-later WITH Bison-exception-2.2"
  "Vulkan-Headers|1.4.363.0|https://github.com/KhronosGroup/Vulkan-Headers/archive/refs/tags/vulkan-sdk-1.4.363.0.tar.gz|4a078be12bef21cfebc09d878b77a63cff9d68f899254a0b00d0e37ef73e7f7e|Apache-2.0 OR MIT"
  "ffmpeg|9.0.2|https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz|8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e|LGPL-2.1-or-later (libmpv), GPL-2.0-or-later (programs)"
  "mpv|0.41.0|https://github.com/mpv-player/mpv/archive/refs/tags/v0.41.0.tar.gz|ee21092a5ee427353392360929dc64645c54479aefdb5babc5cfbb5fad626209|LGPL-2.1-or-later (-Dgpl=false)"
  # The KhronosGroup release build. Building MoltenVK needs Xcode, so we use the release file.
  "MoltenVK|1.4.2|https://github.com/KhronosGroup/MoltenVK/releases/download/v1.4.2/MoltenVK-macos.tar|f95765a6229cb7b915990a2890ce12ebe36a730b021545d3d52ae69ce4c4024e|Apache-2.0"
)
# name|version|repository|commit|license. The clone includes the Git submodules.
GIT_SOURCES=(
  "libplacebo|v7.387.0|https://code.videolan.org/videolan/libplacebo.git|275f7dea27924cdd34ab2e6c20e448b6bc4fab65|LGPL-2.1-or-later"
  "x264|r3222|https://code.videolan.org/videolan/x264.git|b35605ace3ddf7c1a5d67a2eb553f034aef41d55|GPL-2.0-or-later"
)

# --- Checks and environment --------------------------------------------------------------------
if [[ "$(uname -s)" != "Darwin" || "$(uname -m)" != "arm64" ]]; then
  echo "This script is for an Apple Silicon Mac." >&2
  exit 1
fi
for tool in meson ninja cmake pkgconf clang swiftc; do
  command -v "$tool" >/dev/null || { echo "Missing build tool: $tool" >&2; exit 1; }
done

if [[ "${1:-}" == "--clean" ]]; then
  rm -rf "$BUILD" "$STAMPS" "$DEPS" "$LGPL" "$GPL" "$WORK/prefix-mpv"
fi
mkdir -p "$SRC" "$BUILD" "$STAMPS"

JOBS="$(sysctl -n hw.ncpu)"
export MACOSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET"
ARCH_FLAGS="-arch arm64 -mmacosx-version-min=$DEPLOYMENT_TARGET"
export CFLAGS="$ARCH_FLAGS -O2 -I$DEPS/include"
export CXXFLAGS="$CFLAGS"
export LDFLAGS="$ARCH_FLAGS -L$DEPS/lib"
# Only our own .pc files. Homebrew .pc files must not be found.
export PKG_CONFIG="$(command -v pkgconf)"
export PKG_CONFIG_LIBDIR="$DEPS/lib/pkgconfig"
unset PKG_CONFIG_PATH CPATH LIBRARY_PATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH
# CMake must not find the packages of Homebrew.
export CMAKE_IGNORE_PREFIX_PATH="/opt/homebrew:/usr/local"

field() { cut -d'|' -f"$2" <<<"$1"; }
done_stamp() { [[ -f "$STAMPS/$1" ]]; }
mark_done() { touch "$STAMPS/$1"; }
log() { printf '\n=== %s\n' "$*"; }

# --- Sources -----------------------------------------------------------------------------------
archive_file() {
  local url="$1"
  local name="$2" version="$3"
  local ext="${url##*/}"
  case "$ext" in
    *.tar.xz) ext="tar.xz" ;; *.tar.gz) ext="tar.gz" ;; *.tar) ext="tar" ;;
    *) echo "Unknown archive type: $url" >&2; exit 1 ;;
  esac
  echo "$SRC/$name-$version.$ext"
}

fetch_sources() {
  local entry name version url sha file
  for entry in "${TARBALLS[@]}"; do
    name="$(field "$entry" 1)"; version="$(field "$entry" 2)"
    url="$(field "$entry" 3)"; sha="$(field "$entry" 4)"
    file="$(archive_file "$url" "$name" "$version")"
    if [[ ! -f "$file" ]]; then
      curl -fsSL --retry 3 -o "$file.part" "$url"
      mv "$file.part" "$file"
    fi
    echo "$sha  $file" | shasum -a 256 -c - >/dev/null || { echo "Bad SHA-256: $file" >&2; exit 1; }
  done
  local repo commit dir
  for entry in "${GIT_SOURCES[@]}"; do
    name="$(field "$entry" 1)"; repo="$(field "$entry" 3)"; commit="$(field "$entry" 4)"
    dir="$SRC/$name"
    if [[ ! -d "$dir/.git" ]]; then
      git clone -q "$repo" "$dir"
    fi
    if [[ "$(git -C "$dir" rev-parse HEAD)" != "$commit" ]]; then
      git -C "$dir" fetch -q origin
      git -C "$dir" -c advice.detachedHead=false checkout -q "$commit"
    fi
    git -C "$dir" submodule update -q --init --recursive
    [[ "$(git -C "$dir" rev-parse HEAD)" == "$commit" ]] || { echo "Bad commit: $name" >&2; exit 1; }
  done
}

# Extracts a tarball into a clean folder in $BUILD and prints the folder.
unpack() {
  local name="$1" entry
  for entry in "${TARBALLS[@]}"; do
    [[ "$(field "$entry" 1)" == "$name" ]] || continue
    local version url file dir
    version="$(field "$entry" 2)"; url="$(field "$entry" 3)"
    file="$(archive_file "$url" "$name" "$version")"
    dir="$BUILD/$name-$version"
    rm -rf "$dir"; mkdir -p "$dir"
    tar -xf "$file" -C "$dir" --strip-components 1
    echo "$dir"
    return
  done
  echo "Unknown source: $name" >&2; exit 1
}

meson_static() {
  local src="$1" prefix="$2"; shift 2
  meson setup "$src/_build" "$src" --prefix="$prefix" --libdir=lib --buildtype=release \
    --default-library=static -Dprefer_static=true -Db_ndebug=true --wrap-mode=nodownload "$@"
  meson compile -C "$src/_build"
  meson install -C "$src/_build"
}

# --- Dependencies (static) ---------------------------------------------------------------------
build_freetype() {
  local src; src="$(unpack freetype)"
  # zlib from macOS. No PNG, no Brotli, no HarfBuzz: libass needs only the outline fonts.
  meson_static "$src" "$DEPS" -Dzlib=system -Dbzip2=disabled -Dpng=disabled -Dbrotli=disabled \
    -Dharfbuzz=disabled -Dtests=disabled
}

build_fribidi() {
  local src; src="$(unpack fribidi)"
  meson_static "$src" "$DEPS" -Ddocs=false -Dbin=false -Dtests=false
}

build_harfbuzz() {
  local src; src="$(unpack harfbuzz)"
  meson_static "$src" "$DEPS" -Dfreetype=enabled -Dglib=disabled -Dgobject=disabled \
    -Dcairo=disabled -Dchafa=disabled -Dicu=disabled -Dgraphite=disabled -Dgraphite2=disabled \
    -Dcoretext=disabled -Dpng=disabled -Dzlib=disabled -Dtests=disabled -Ddocs=disabled \
    -Dutilities=disabled -Dintrospection=disabled -Dbenchmark=disabled -Dsubset=disabled \
    -Draster=disabled -Dvector=disabled -Dgpu=disabled -Dwasm=disabled -Dfontations=disabled \
    -Dharfrust=disabled -Dkbts=disabled -Dgdi=disabled -Ddirectwrite=disabled
}

build_libass() {
  local src; src="$(unpack libass)"
  # CoreText finds the fonts of macOS. No fontconfig.
  meson_static "$src" "$DEPS" -Dfontconfig=disabled -Ddirectwrite=disabled -Dcoretext=enabled \
    -Dlibunibreak=disabled -Drequire-system-font-provider=true -Dasm=enabled -Dtest=disabled \
    -Dcompare=disabled -Dprofile=disabled -Dfuzz=disabled -Dcheckasm=disabled
}

build_lcms2() {
  local src; src="$(unpack lcms2)"
  meson_static "$src" "$DEPS" -Djpeg=disabled -Dtiff=disabled -Dutils=false -Dtests=disabled \
    -Dfastfloat=false -Dthreaded=false
}

build_glslang() {
  local src; src="$(unpack glslang)"
  # No SPIRV-Tools optimizer (ENABLE_OPT=OFF). libplacebo does not need it.
  cmake -S "$src" -B "$src/_build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$DEPS" -DCMAKE_INSTALL_LIBDIR=lib \
    -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DBUILD_SHARED_LIBS=OFF -DENABLE_OPT=OFF -DGLSLANG_TESTS=OFF -DGLSLANG_ENABLE_INSTALL=ON \
    -DENABLE_GLSLANG_BINARIES=OFF -DENABLE_HLSL=OFF -DBUILD_EXTERNAL=OFF
  cmake --build "$src/_build"
  cmake --install "$src/_build"
}

build_vulkan() {
  local src; src="$(unpack Vulkan-Headers)"
  mkdir -p "$DEPS/include" "$DEPS/share/vulkan" "$DEPS/lib/pkgconfig"
  rm -rf "$DEPS/include/vulkan" "$DEPS/include/vk_video"
  cp -R "$src/include/vulkan" "$src/include/vk_video" "$DEPS/include/"
  rm -rf "$DEPS/share/vulkan/registry"
  cp -R "$src/registry" "$DEPS/share/vulkan/registry"

  # libmpv links MoltenVK directly. There is no Vulkan loader.
  local mvk; mvk="$(unpack MoltenVK)"
  lipo -thin arm64 "$mvk/MoltenVK/dynamic/dylib/macOS/libMoltenVK.dylib" -output "$DEPS/lib/libMoltenVK.dylib"
  cp "$mvk/LICENSE" "$DEPS/share/vulkan/MoltenVK-LICENSE"

  local api_version="${src##*-}"
  cat >"$DEPS/lib/pkgconfig/vulkan.pc" <<EOF
prefix=$DEPS
includedir=\${prefix}/include
libdir=\${prefix}/lib

Name: Vulkan (MoltenVK)
Description: Vulkan headers ${api_version} and the MoltenVK driver
Version: ${api_version%.*}
Libs: -L\${libdir} -lMoltenVK
Cflags: -I\${includedir}
EOF
}

build_libplacebo() {
  local src="$BUILD/libplacebo"
  rm -rf "$src"; cp -R "$SRC/libplacebo" "$src"
  # meson finds the glslang libraries only in the "vulkan-sdk" folder. Two find_library calls do not
  # use that folder, so add it to them.
  sed -i '' \
    -e "s/cxx.find_library('glslang-default-resource-limits', required: false)/cxx.find_library('glslang-default-resource-limits', required: false, dirs: [get_option('vulkan-sdk') \/ 'lib'])/" \
    -e "s/cxx.find_library('glslang', required: required, static: static)/cxx.find_library('glslang', required: required, static: static, dirs: vulkan_lib_dirs)/" \
    "$src/src/glsl/meson.build"
  grep -q "find_library('glslang', required: required, static: static, dirs: vulkan_lib_dirs)" \
    "$src/src/glsl/meson.build" || { echo "The libplacebo patch does not apply." >&2; exit 1; }
  meson_static "$src" "$DEPS" -Dvulkan-sdk="$DEPS" -Dvulkan=enabled -Dvulkan-registry="$DEPS/share/vulkan/registry/vk.xml" \
    -Dvk-proc-addr=enabled -Dopengl=disabled -Dd3d11=disabled -Dglslang=enabled -Dshaderc=disabled \
    -Dlcms=enabled -Ddovi=enabled -Dlibdovi=disabled -Ddemos=false -Dtests=false -Dbench=false \
    -Dfuzz=false -Dunwind=disabled -Dxxhash=disabled
}

# --- FFmpeg ------------------------------------------------------------------------------------
FFMPEG_COMMON=(
  --enable-static --disable-shared --enable-pic
  --disable-doc --disable-debug
  # No automatic detection: only the features below. Threads are also in the automatic list.
  --disable-autodetect --enable-pthreads
  --enable-videotoolbox --enable-audiotoolbox --enable-zlib --enable-bzlib --enable-iconv
  # configure finds iconv in libc, but on macOS iconv is in the system library libiconv.
  --extra-libs=-liconv
  --arch=arm64 --cc=clang --cxx=clang++
  "--extra-cflags=$ARCH_FLAGS" "--extra-ldflags=$ARCH_FLAGS"
  --pkg-config-flags=--static
)

build_ffmpeg_lgpl() {
  local src; src="$(unpack ffmpeg)"
  # The libraries for libmpv. LGPL: no --enable-gpl, no --enable-nonfree. libmpv plays local files only.
  (cd "$src" && ./configure --prefix="$LGPL" "${FFMPEG_COMMON[@]}" \
    --disable-programs --disable-network && make -j"$JOBS" && make install)
}

build_x264() {
  local src="$BUILD/x264"
  rm -rf "$src"; cp -R "$SRC/x264" "$src"
  (cd "$src" && ./configure --prefix="$GPL" --enable-static --enable-pic --disable-cli \
    --extra-cflags="$ARCH_FLAGS" --extra-ldflags="$ARCH_FLAGS" && make -j"$JOBS" && make install)
}

build_ffmpeg_gpl() {
  local src; src="$(unpack ffmpeg)"
  # The programs for the export. GPL because of x264. The app runs them as separate processes.
  (cd "$src" && PKG_CONFIG_LIBDIR="$GPL/lib/pkgconfig:$DEPS/lib/pkgconfig" ./configure --prefix="$GPL" \
    "${FFMPEG_COMMON[@]}" --enable-gpl --enable-libx264 --enable-libass --disable-ffplay --disable-network \
    && make -j"$JOBS" && make install)
}

# --- mpv ---------------------------------------------------------------------------------------
build_mpv() {
  local src; src="$(unpack mpv)"
  # libmpv only (no mpv program), LGPL, no Lua, no JavaScript: the app uses only the client API.
  # The native file stops meson from using sdl2-config of Homebrew.
  printf '[binaries]\nsdl2-config = %s\n' "'/usr/bin/false'" >"$src/native.ini"
  # iconv is a macOS system library with no static file, so prefer_static does not find it.
  # The static FFmpeg libraries also need it.
  PKG_CONFIG_LIBDIR="$LGPL/lib/pkgconfig:$DEPS/lib/pkgconfig" LDFLAGS="$LDFLAGS -liconv" \
    meson setup "$src/_build" "$src" --native-file "$src/native.ini" \
    --prefix="$WORK/prefix-mpv" --libdir=lib --buildtype=release \
    --default-library=shared -Dprefer_static=true -Db_ndebug=true --wrap-mode=nodownload \
    -Dgpl=false -Dlibmpv=true -Dcplayer=false -Dtests=false -Dfuzzers=false \
    -Dbuild-date=false -Dmanpage-build=disabled -Dhtml-build=disabled -Dpdf-build=disabled \
    -Dlua=disabled -Djavascript=disabled -Dlibarchive=disabled -Dlibbluray=disabled \
    -Duchardet=disabled -Drubberband=disabled -Dzimg=disabled -Djpeg=disabled -Dvapoursynth=disabled \
    -Dsdl2-audio=disabled -Dsdl2-video=disabled -Dsdl2-gamepad=disabled -Dlibavdevice=enabled \
    -Dlcms2=enabled -Dvulkan=enabled -Dcocoa=enabled -Dswift-build=enabled -Dmacos-cocoa-cb=enabled \
    "-Dswift-flags=-target arm64-apple-macos$DEPLOYMENT_TARGET" -Dvideotoolbox-pl=enabled \
    -Dcoreaudio=enabled -Daudiounit=disabled -Diconv=enabled
  meson compile -C "$src/_build"
  rm -rf "$WORK/prefix-mpv"
  meson install -C "$src/_build"
}

# --- Output and checks -------------------------------------------------------------------------
assemble() {
  rm -rf "$OUT/mpv" "$OUT/ffmpeg" "$OUT/LICENSES" "$OUT/BUILD-INFO.txt"
  mkdir -p "$OUT/mpv" "$OUT/ffmpeg/bin" "$OUT/LICENSES"

  local lib="$OUT/mpv/libmpv.2.dylib"
  cp "$WORK/prefix-mpv/lib/libmpv.2.dylib" "$lib"
  cp "$DEPS/lib/libMoltenVK.dylib" "$OUT/mpv/libMoltenVK.dylib"
  ln -s libmpv.2.dylib "$OUT/mpv/libmpv.dylib"
  chmod u+w "$lib"
  install_name_tool -id @rpath/libmpv.2.dylib "$lib"
  local dep
  for dep in $(otool -L "$lib" | awk 'NR > 1 { print $1 }' | grep -E 'libMoltenVK|libvulkan'); do
    install_name_tool -change "$dep" @rpath/libMoltenVK.dylib "$lib"
  done
  install_name_tool -add_rpath @loader_path "$lib" 2>/dev/null || true
  strip -x "$lib"
  codesign --force --sign - "$lib"
  codesign --force --sign - "$OUT/mpv/libMoltenVK.dylib"

  cp "$GPL/bin/ffmpeg" "$GPL/bin/ffprobe" "$OUT/ffmpeg/bin/"
  strip "$OUT/ffmpeg/bin/ffmpeg" "$OUT/ffmpeg/bin/ffprobe"
  codesign --force --sign - "$OUT/ffmpeg/bin/ffmpeg" "$OUT/ffmpeg/bin/ffprobe"

  copy_licenses
  write_build_info
}

copy_licenses() {
  local l="$OUT/LICENSES"
  cp "$BUILD/mpv-0.41.0/LICENSE.LGPL" "$l/mpv-LGPL-2.1.txt"
  cp "$BUILD/mpv-0.41.0/Copyright" "$l/mpv-Copyright.txt"
  cp "$BUILD/ffmpeg-9.0.2/COPYING.LGPLv2.1" "$l/FFmpeg-LGPL-2.1.txt"
  cp "$BUILD/ffmpeg-9.0.2/COPYING.GPLv2" "$l/FFmpeg-GPL-2.0.txt"
  cp "$BUILD/ffmpeg-9.0.2/LICENSE.md" "$l/FFmpeg-LICENSE.md"
  cp "$BUILD/libplacebo/LICENSE" "$l/libplacebo-LGPL-2.1.txt"
  cp "$BUILD/libass-0.17.5/COPYING" "$l/libass-ISC.txt"
  cp "$BUILD/freetype-2.15.0/docs/FTL.TXT" "$l/FreeType-FTL.txt"
  cp "$BUILD/fribidi-1.0.18/COPYING" "$l/FriBidi-LGPL-2.1.txt"
  cp "$BUILD/harfbuzz-15.0.0/COPYING" "$l/HarfBuzz-MIT.txt"
  cp "$BUILD/lcms2-2.19.1/LICENSE" "$l/LittleCMS-MIT.txt"
  cp "$BUILD/glslang-16.6.0/LICENSE.txt" "$l/glslang-LICENSE.txt"
  cp "$BUILD/Vulkan-Headers-1.4.363.0/LICENSE.md" "$l/Vulkan-Headers-LICENSE.md"
  cp "$DEPS/share/vulkan/MoltenVK-LICENSE" "$l/MoltenVK-Apache-2.0.txt"
  cp "$BUILD/x264/COPYING" "$l/x264-GPL-2.0.txt"
}

write_build_info() {
  local entry
  {
    echo "BananaShot native files for macOS arm64 (B-13)"
    echo "Built with distribution/macos/build-natives.sh, minimum macOS $DEPLOYMENT_TARGET"
    echo "Built on: macOS $(sw_vers -productVersion), $(clang --version | head -1)"
    echo
    echo "libmpv (mpv/libmpv.2.dylib): LGPL-2.1-or-later. mpv -Dgpl=false. It contains FFmpeg"
    echo "(LGPL, no --enable-gpl), libplacebo, libass, FreeType, FriBidi, HarfBuzz, Little CMS,"
    echo "and glslang as static libraries. mpv/libMoltenVK.dylib: Apache-2.0."
    echo "ffmpeg/bin/ffmpeg and ffprobe: GPL-2.0-or-later (--enable-gpl, libx264, libass)."
    echo
    echo "Sources (name, version, URL or repository, SHA-256 or Git commit, license):"
    for entry in "${TARBALLS[@]}" "${GIT_SOURCES[@]}"; do
      echo "- $(field "$entry" 1) $(field "$entry" 2): $(field "$entry" 3)"
      echo "  $(field "$entry" 4); $(field "$entry" 5)"
    done
    echo
    echo "FFmpeg configuration of the programs:"
    "$OUT/ffmpeg/bin/ffmpeg" -hide_banner -buildconf 2>/dev/null | sed -n '2,$p' | sed 's/^ */  /'
  } >"$OUT/BUILD-INFO.txt"
}

verify() {
  local failed=0 file minos deps
  while IFS= read -r file; do
    file -b "$file" | grep -q "Mach-O" || continue
    if [[ "$(lipo -archs "$file")" != "arm64" ]]; then
      echo "Not arm64 only: $file ($(lipo -archs "$file"))" >&2; failed=1
    fi
    minos="$(vtool -show-build "$file" | awk '/minos/ { print $2; exit }')"
    if [[ "$(printf '%s\n%s\n' "$minos" "$DEPLOYMENT_TARGET" | sort -V | tail -1)" != "$DEPLOYMENT_TARGET" ]]; then
      echo "Minimum macOS $minos is newer than $DEPLOYMENT_TARGET: $file" >&2; failed=1
    fi
    deps="$(otool -L "$file" | awk 'NR > 1 { print $1 }' \
      | grep -vE '^(/usr/lib/|/System/Library/|@rpath/libMoltenVK\.dylib$|@rpath/libmpv\.2\.dylib$)' || true)"
    if [[ -n "$deps" ]]; then
      echo "Not allowed library in $file:" >&2; echo "$deps" >&2; failed=1
    fi
    codesign --verify "$file" || { echo "No valid signature: $file" >&2; failed=1; }
  done < <(find "$OUT/mpv" "$OUT/ffmpeg" -type f)

  local filters encoders
  filters="$("$OUT/ffmpeg/bin/ffmpeg" -hide_banner -filters)"
  encoders="$("$OUT/ffmpeg/bin/ffmpeg" -hide_banner -encoders)"
  grep -qw "subtitles" <<<"$filters" || { echo "No subtitles filter." >&2; failed=1; }
  grep -qw "libx264" <<<"$encoders" || { echo "No libx264 encoder." >&2; failed=1; }
  grep -qw "h264_videotoolbox" <<<"$encoders" || { echo "No VideoToolbox encoder." >&2; failed=1; }
  if "$OUT/ffmpeg/bin/ffmpeg" -hide_banner -buildconf | grep -q -- "--enable-nonfree"; then
    echo "FFmpeg must not have --enable-nonfree." >&2; failed=1
  fi

  # libmpv must not contain GPL code. mpv stores its meson options. FFmpeg stores its license.
  local text
  text="$(strings "$OUT/mpv/libmpv.2.dylib")"
  grep -q -- "-Dgpl=false" <<<"$text" || { echo "libmpv is not built with -Dgpl=false." >&2; failed=1; }
  grep -q "LGPL version 2.1 or later" <<<"$text" || { echo "The FFmpeg in libmpv is not LGPL." >&2; failed=1; }
  # The H.264 decoder reads the x264 version in files, so look for the encoder, not for the word x264.
  if grep -qE "^libx264$|x264_encoder_open" <<<"$text"; then
    echo "libmpv contains the x264 encoder." >&2; failed=1
  fi

  # Load libmpv and start a player with no video output.
  python3 -I - "$OUT/mpv/libmpv.dylib" <<'PY' || { echo "libmpv does not start." >&2; failed=1; }
import ctypes, sys
mpv = ctypes.CDLL(sys.argv[1])
mpv.mpv_create.restype = ctypes.c_void_p
mpv.mpv_get_property_string.restype = ctypes.c_void_p
for name in ("mpv_initialize", "mpv_set_option_string", "mpv_get_property_string", "mpv_free",
             "mpv_terminate_destroy"):
    getattr(mpv, name).argtypes = None
handle = ctypes.c_void_p(mpv.mpv_create())
mpv.mpv_set_option_string(handle, b"vo", b"null")
mpv.mpv_set_option_string(handle, b"ao", b"null")
if mpv.mpv_initialize(handle) != 0:
    sys.exit(1)
for prop in (b"mpv-version", b"ffmpeg-version"):
    value = mpv.mpv_get_property_string(handle, prop)
    print(prop.decode(), ctypes.string_at(value).decode())
    mpv.mpv_free(ctypes.c_void_p(value))
mpv.mpv_terminate_destroy(handle)
PY
  return "$failed"
}

# --- Main --------------------------------------------------------------------------------------
log "Sources"
fetch_sources
for stage in freetype fribidi harfbuzz libass lcms2 glslang vulkan libplacebo ffmpeg_lgpl x264 ffmpeg_gpl mpv; do
  if done_stamp "$stage"; then
    echo "Skip $stage (done)"
    continue
  fi
  log "Build $stage"
  "build_$stage"
  mark_done "$stage"
done
log "Output"
assemble
log "Checks"
verify
echo
echo "The native files are in $OUT"
