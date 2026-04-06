# ZipFont

ZipFont is an Android app that generates a FlipFont APK from a selected custom font file.

<p align="center">
  <img src="docs/home.jpg" alt="Home Screen" width="260" />
  <img src="docs/home_2.jpg" alt="Home Screen State" width="260" />
  <img src="docs/steps.jpg" alt="Steps Screen" width="260" />
</p>

## Features

- Pick a custom font file (.ttf/.otf)
- Inject font into FlipFont skeleton APK
- Align and sign generated APK
- Save output to Downloads/ZipFont
- In-app install flow and guided steps screen

## Tech Stack

- Kotlin
- Jetpack Compose
- Android SDK tools (zipalign/apksig via library flow)

## Build

Debug build:

    ./gradlew :app:assembleDebug

Release build (PowerShell):

    $env:RELEASE_STORE_FILE="release.jks"
    $env:RELEASE_STORE_PASSWORD="your_store_password"
    $env:RELEASE_KEY_ALIAS="release"
    $env:RELEASE_KEY_PASSWORD="your_key_password"
    ./gradlew :app:assembleRelease

## Output

Generated font APK is saved to:

    Downloads/ZipFont/final_font.apk

---
Built by [kunalkcube](https://github.com/kunalkcube)
