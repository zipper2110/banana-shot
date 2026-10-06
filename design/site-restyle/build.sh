#!/bin/sh
# Makes option-*.html from site/public/index.html. The text of the page does not change.
# option-current.html uses the site files as they are.
# The options use copies of the site files in accent/, with the app accent (#C8EC46) in place of the old lime.
# The accent shades come from the seed math of Palette.kt.
cd "$(dirname "$0")"
SITE=../../site/public
ACCENT='s/#a1fe00/#c8ec46/gI; s/#b4ff33/#d0ed68/gI; s/#142000/#191e09/gI; s/161, *254, *0/200, 236, 70/g; s/#c6ff5c/#d8f27a/gI; s/#a3c586/#a7b78e/gI; s/#b4c79a/#bcc49f/gI'

mkdir -p accent/demos
sed -e "$ACCENT" $SITE/assets/site.css > accent/site.css
sed -e "$ACCENT" $SITE/assets/demos.css > accent/demos.css
sed -e "$ACCENT" $SITE/assets/logo.svg > accent/logo.svg
for f in $SITE/assets/demos/*.js; do sed -e "$ACCENT" "$f" > accent/demos/$(basename "$f"); done

for opt in current a b c; do
  if [ "$opt" = current ]; then
    sed -e 's#"/assets/#"../../site/public/assets/#g' \
        -e 's#^  <link rel="stylesheet" href="../../site/public/assets/demos.css">#&\n  <script src="fix-paths.js"></script>#' \
        $SITE/index.html > option-current.html
  else
    sed -e "s#<html lang=\"en\">#<html lang=\"en\" data-option=\"$opt\">#" \
        -e 's#"/assets/site.css"#"accent/site.css"#; s#"/assets/demos.css"#"accent/demos.css"#' \
        -e 's#"/assets/demos/#"accent/demos/#g; s#"/assets/logo.svg"#"accent/logo.svg"#g' \
        -e 's#"/assets/#"../../site/public/assets/#g' \
        -e "s#^  <link rel=\"stylesheet\" href=\"accent/demos.css\">#&\n  <link rel=\"stylesheet\" href=\"option-$opt.css\">\n  <script src=\"fix-paths.js\"></script>#" \
        $SITE/index.html > option-$opt.html
  fi
  # Options A and B have a switch for the background effect (mockup only).
  if [ "$opt" = a ] || [ "$opt" = b ]; then
    sed -i 's#^  <script src="fix-paths.js"></script>#&\n  <script src="option-bg.js"></script>#' option-$opt.html
  fi
done
