# Recording a demo

How to record the countdown for the README, a release post or an issue. The screenshots in
`docs/screenshots/` are not recorded by hand: they are the Roborazzi reference images and CI checks
them on every pull request (`./gradlew recordRoborazziDebug` re-creates them).

## 1. Clean status bar

System UI demo mode freezes the clock, battery and signal icons and hides notification icons:

```sh
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0730
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

One UI may ignore parts of demo mode; check the first frames before recording a long take.

## 2. Something to show

A countdown needs real departures: record during operating hours with a route whose window is
active, or watch a route from its screen (bell icon). For the reminders, set the window to start a
few minutes from now. Use a test route, not your real home address – it is visible in the
recording.

## 3. Record

```sh
adb shell screenrecord --bit-rate 8000000 --time-limit 30 /sdcard/zeitig.mp4
adb pull /sdcard/zeitig.mp4 && adb shell rm /sdcard/zeitig.mp4
```

`screenrecord` stops after `--time-limit` seconds (at most 180). [scrcpy](https://github.com/Genymobile/scrcpy)
records from the computer instead: `scrcpy --no-audio --record zeitig.mp4`.

## 4. Convert for GitHub

Animated images in the repository should stay small (aim for under 5 MB). A two-pass palette keeps
GIFs sharp:

```sh
ffmpeg -i zeitig.mp4 -vf "fps=15,scale=360:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer" -loop 0 docs/images/demo.gif
```

For an issue or a pull request, drag the MP4 into the comment instead: GitHub plays it inline.

## 5. Back to normal

```sh
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell settings put global sysui_demo_allowed 0
```
