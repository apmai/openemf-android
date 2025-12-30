# OpenEMF Android

<p align="center">
  <img src="logo.png" alt="OpenEMF Logo" width="400">
</p>

<p align="center">
  <strong>Measure, Mitigate and Educate</strong>
</p>

An open-source EMF (electromagnetic field) monitoring app for Android. Measure WiFi, Bluetooth, Cellular, and Magnetic field exposure with a simple E-Score rating system.

## Features

- **E-Score Rating**: Simple 0-100 score based on ICNIRP reference levels
  - 0-10: Excellent (Very low exposure)
  - 11-25: Good (Low exposure)
  - 26-50: Moderate (Typical urban levels)
  - 51-75: Elevated (Above average)
  - 76-90: High (Consider reducing sources)
  - 90+: Very High

- **Source Detection**: Scans for WiFi networks, Bluetooth devices, and cellular towers

- **Magnetic Field Reading**: Uses device magnetometer to measure ELF-EMF

- **Statistics & History**: Track your exposure over time with charts and trends

- **Map View**: See measurement locations on an interactive map

- **Mitigation Tips**: Practical suggestions to reduce EMF exposure

## Download

Get the latest APK from [Releases](https://github.com/apmai/openemf-android/releases/latest).

## Building from Source

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 34

### Build Steps

```bash
# Clone the repository
git clone https://github.com/apmai/openemf-android.git
cd openemf-android

# Build debug APK
./gradlew assembleDebug

# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with StateFlow
- **DI**: Hilt
- **Database**: Room
- **Maps**: OSMDroid (OpenStreetMap)

## Permissions

The app requires the following permissions:
- `ACCESS_FINE_LOCATION` - For WiFi/cellular scanning and map features
- `ACCESS_COARSE_LOCATION` - Location-based features
- `BLUETOOTH_SCAN` - Bluetooth device detection (Android 12+)
- `BLUETOOTH_CONNECT` - Bluetooth connectivity info (Android 12+)

## Algorithm

The E-Score is calculated using a logarithmic scale based on cumulative signal strength:

```
score = log10(exposure * 1000 + 1) / log10(1001) * 100
```

This provides a normalized 0-100 score where lower is better.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Related Projects

- [Invisible Rainbows](https://invisiblerainbows.com) - EMF education and research
- [OpenEMF Web](https://openemf.invisiblerainbows.com) - Web companion app

## License

BSD 3-Clause License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- Inspired by [ElectroSmart](https://github.com/arnaudlegout/electrosmart)
- Based on research from [ICNIRP](https://www.icnirp.org/) and [BioInitiative](https://bioinitiative.org/)
