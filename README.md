# Pinyin Peek

Point your camera at Chinese text → see pinyin + English translation instantly.

- **OCR**: ML Kit Chinese text recognition (on-device)
- **Pinyin**: ICU Han-Latin transliterator with tone marks
- **Translation**: ML Kit on-device zh→en (downloads ~30MB model once)
- **Dictionary**: Room database with accent-insensitive pinyin search
- **Pleco**: x-callback-url integration for lookup and flashcards
- **All on-device**: no network calls for pinyin, OCR, or dictionary

## Build

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Stack

- Kotlin + Jetpack Compose + CameraX
- ML Kit: text-recognition-chinese + translate
- Room (SQLite) for dictionary storage
- ICU4J Transliterator for Han-Latin pinyin