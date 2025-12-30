# OpenEMF Android

<p align="center">
  <img src="logo.png" alt="OpenEMF Logo" width="400">
</p>

<p align="center">
  <strong>Measure, Mitigate and Educate</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Status-BETA-yellow" alt="Beta Status">
  <img src="https://img.shields.io/badge/License-BSD%203--Clause-blue" alt="License">
  <img src="https://img.shields.io/badge/Android-8.0%2B-green" alt="Android 8.0+">
</p>

> **Note:** OpenEMF is currently in **beta**. We're actively seeking early testers to help improve the app. Your feedback is invaluable!

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

## Download & Install

Get the latest APK from [Releases](https://github.com/apmai/openemf-android/releases/latest).

### Installation Steps

1. **Download** the `app-release.apk` file on your Android device
2. **Enable installation** from unknown sources:
   - Go to Settings → Security → Install unknown apps
   - Enable for your browser or file manager
3. **Open** the downloaded APK file
4. **Tap Install** when prompted
5. **Grant permissions** when the app launches:
   - Location (required for WiFi/cellular scanning)
   - Bluetooth (for device detection)

### Testing Checklist

After installing, verify these features work:
- [ ] App launches without crash
- [ ] E-Score gauge displays and animates
- [ ] WiFi networks are detected (Sources tab)
- [ ] Bluetooth devices are found
- [ ] Map view loads (requires location enabled)
- [ ] Statistics show measurement history
- [ ] Dark/light theme toggle works

### Troubleshooting

| Issue | Solution |
|-------|----------|
| "App not installed" | Enable "Install unknown apps" in Settings |
| No WiFi networks found | Grant Location permission, enable GPS |
| No Bluetooth devices | Grant Bluetooth permission, enable Bluetooth |
| Map not loading | Enable Location services on device |

## FAQ

**Q: Is OpenEMF free?**
A: Yes, completely free and open source under BSD 3-Clause license.

**Q: Does it work without internet?**
A: Yes, all measurements are done locally on your device. Internet is only needed to download the app.

**Q: Why does it need Location permission?**
A: Android requires Location permission to scan WiFi networks and cellular towers. This is an Android security requirement, not our choice. Your location data stays on your device.

**Q: Is my data sent anywhere?**
A: No, all data stays on your device. We don't collect any personal information or measurements.

**Q: Why are WiFi scans limited?**
A: Android throttles WiFi scans to 4 per 2 minutes (since Android 9). This is a system limitation we cannot bypass.

**Q: Can I use this to find hidden cameras?**
A: OpenEMF detects WiFi and Bluetooth signals, which some hidden cameras emit. However, it's not specifically designed for this purpose.

**Q: Why is my E-Score different in different rooms?**
A: EMF levels vary based on proximity to routers, cell towers, and other devices. Moving away from sources reduces exposure.

**Q: Is there an iOS version?**
A: No, iOS restricts access to WiFi and cellular scanning APIs, making a comparable app impossible.

**Q: How accurate are the measurements?**
A: The app uses your phone's built-in sensors. While not as precise as professional equipment, it provides reliable relative measurements for comparing environments.

**Q: What do the E-Score levels mean?**
A:
- 0-10: Excellent (very low exposure)
- 11-25: Good (low exposure)
- 26-50: Moderate (typical urban levels)
- 51-75: Elevated (above average)
- 76-90: High (consider reducing sources)
- 90+: Very High

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

## Features in Development

We're actively working on new features for upcoming releases:

| Feature | Status | Target |
|---------|--------|--------|
| Cloud sync & backup | In Progress | v1.1.0 |
| Global EMF heatmap | Planned | v1.1.0 |
| Real-time exposure alerts | Planned | v1.2.0 |
| Widgets for home screen | Planned | v1.2.0 |
| Wear OS companion app | Exploring | TBD |
| Export data (CSV/PDF) | Planned | v1.1.0 |
| Multiple location profiles | Planned | v1.2.0 |

**Want to see the future vision?** Check out our [interactive demo mockup](https://www.invisiblerainbows.com/openemf) to preview upcoming features and UI concepts.

## Beta Feedback

We're actively looking for feedback from early testers! If you encounter any issues or have suggestions:

1. **Report Bugs**: [Open an issue](https://github.com/apmai/openemf-android/issues/new?template=bug_report.md)
2. **Request Features**: [Submit a feature request](https://github.com/apmai/openemf-android/issues/new?template=feature_request.md)
3. **General Feedback**: [Start a discussion](https://github.com/apmai/openemf-android/discussions)

Your feedback helps us improve OpenEMF for everyone!

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
