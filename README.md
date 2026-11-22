# SB Kit

Small client mod where every feature is one class.

## Add a feature
1. Create `src/main/java/dev/sbkit/features/MyFeature.java` extending `Feature`.
2. Declare settings with `add(new Setting.Bool/Int/Text/Choice(...))` — they get saved to `config/sbkit.json` and appear in the settings screen automatically.
3. Override only the hooks you need: `init`, `onStartTick`, `onTick`, `onMessage`, `onWorldChange`.
4. Add `new MyFeature()` to `Features.ALL`. Nothing else changes.

Open settings: `/sbkit`, `/<featureId>` (`/fastleap`, `/ragswap`), or the "Open SB Kit settings" keybind.

## Build
Needs JDK 25 and Gradle 9.5+: `gradle build` -> `build/libs/sbkit-1.0.0.jar`. Remove the old `fastleap-1.0.0.jar` from your mods folder.
