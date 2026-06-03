#!/usr/bin/env bash
# Build the full Loa Loa Loa icon set from the Logo B SVG masters.
# Requires: Google Chrome (headless SVG render) + ImageMagick 7 (magick).
# Run from anywhere; paths are resolved relative to this script.
set -euo pipefail

CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RES="$HERE/../../app/src/main/res"
PLAY="$HERE/../../app/play"
cd "$HERE"

render() { # wrapper.html -> out.png at 1024
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
    --default-background-color=00000000 --window-size=1024,1024 \
    --screenshot="$2" "file://$HERE/$1" 2>/dev/null
}

echo "» rendering masters (1024)"
render _render-icon.html master-1024.png
render _render-mono.html mono-1024.png

echo "» building corner masks"
magick -size 1024x1024 xc:black -fill white -draw "roundrectangle 0,0,1023,1023,184,184" roundmask-1024.png
magick -size 1024x1024 xc:black -fill white -draw "circle 512,512 512,0" circlemask-1024.png

# density -> px (48dp base)
densities=(mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192)

echo "» Play Store store icon 512"
magick master-1024.png -resize 512x512 "$PLAY/icon-512.png"

echo "» per-density launcher layers"
for d in "${densities[@]}"; do
  name="${d%%:*}"; px="${d##*:}"; dir="$RES/mipmap-$name"
  # adaptive foreground (full teal square + mark)
  magick master-1024.png -resize ${px}x${px} "$dir/ic_launcher_foreground.png"
  # legacy square, rounded corners
  magick master-1024.png roundmask-1024.png -alpha off -compose CopyOpacity -composite -resize ${px}x${px} "$dir/ic_launcher.png"
  # legacy round
  magick master-1024.png circlemask-1024.png -alpha off -compose CopyOpacity -composite -resize ${px}x${px} "$dir/ic_launcher_round.png"
  # monochrome (transparent + mark silhouette)
  magick mono-1024.png -resize ${px}x${px} "$dir/ic_launcher_monochrome.png"
  echo "   $name ${px}px ✓"
done

echo "✅ icons built"
