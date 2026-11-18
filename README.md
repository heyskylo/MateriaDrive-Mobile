# Materia Drive Mobile

The iOS app for [Materia Drive](https://drive.materia.bar), built with **Compose Multiplatform**, so the UI is Google's own
Material 3 Expressive implementation: `MaterialExpressiveTheme` with the expressive motion scheme, morphing loading indicators,
the FAB menu, wavy progress and `MaterialShapes`.

- `composeApp/` holds the app (Kotlin, `commonMain`), compiled to an iOS framework.
- `iosApp/` is the SwiftUI host plus an [XcodeGen](https://github.com/yonaskolb/XcodeGen) spec. The Xcode project is generated on CI.
- `.github/workflows/ios.yml` builds on a macOS runner and uploads an **unsigned** `.ipa` artifact.

## Install on a device (signed locally; the certificate never goes to GitHub)

```sh
gh run download -n Materia-unsigned-ipa -D dist          # latest successful build
zsign -k cert.p12 -p '<password>' -m profile.mobileprovision \
      -b app.salmon632.kumquat9999 -o dist/Materia-signed.ipa dist/Materia-unsigned.ipa
ideviceinstaller -i dist/Materia-signed.ipa
```

The bundle ID must match the App ID in your provisioning profile.
