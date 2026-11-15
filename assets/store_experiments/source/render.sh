#!/bin/bash
# Renders every screen of index.html to ../<n>.png and the feature graphic to
# ../feature_graphic.png with headless Chrome.
cd "$(dirname "$0")"
for n in 1 2 3 4 5 6; do
  google-chrome --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --window-size=1080,1920 --virtual-time-budget=8000 \
    --screenshot="$PWD/../$n.png" "file://$PWD/index.html?s=$n" 2>/dev/null
done
google-chrome --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,500 --virtual-time-budget=8000 \
  --screenshot="$PWD/../feature_graphic.png" "file://$PWD/index.html?s=feature" 2>/dev/null
