# Zombie City Sandbox

A top-down sandbox for Android: drop zombies into a procedurally generated city and watch civilians,
cops and the military try to survive.

**Download:** [`release/ZombieCitySandbox.apk`](release/ZombieCitySandbox.apk). To install it, allow
"Install unknown apps" for your browser or file manager, then open the APK.

## How to play

- **Pick a tool** in the bottom bar, then **tap or drag on the city**:
  - **Civilian**: wanders the streets and runs from zombies.
  - **Cop**: pistol with a 12-round magazine. Hunts zombies nearby and backs off when they get close.
  - **Military**: rifle fired in 3-round bursts, longer range, more health, and grenades for big crowds.
  - **Zombie**: slow shambler that follows the scent of people across the city.
  - **Runner**: fast, fragile zombie.
  - **Brute**: huge, slow and hard to kill. Its hits knock people back.
  - **Bomb**: explosion at the tap point.
  - **Erase**: removes people, zombies and corpses under your finger.
  - **Move**: drag to pan. Tap someone to follow them with the camera.
- **Pinch** to zoom and **drag with two fingers** to pan, whichever tool is selected.
- Top buttons: **Pause/Play**, **Speed** (1x/2x/4x), **Brush** (spawn 1/5/10 at a time),
  **Clear** (remove everyone), **New City**.

A person killed by a zombie gets up again as a zombie a few seconds later. Bites can also infect: an
infected person glows green and turns within about 12–26 seconds, even if they escape.

## Building

No Gradle or Android Studio needed. On Ubuntu/Debian:

```sh
sudo apt-get install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
./build.sh   # -> release/ZombieCitySandbox.apk
```

The APK is signed with the debug keystore in `keystore/` (password `android`), so newer builds
install as updates over older ones. GitHub Actions also builds the APK on every push
(`.github/workflows/build-apk.yml`).

Code layout (`src/com/instantaxtion/zombiesandbox/`):

- `City.java`: city generation, the pre-rendered map, line of sight and BFS flow fields.
- `World.java`: simulation (AI, shooting, infection, explosions, particles).
- `GameView.java`: rendering, camera, touch input and UI.
- `Entity.java`: data for a single person or zombie.
