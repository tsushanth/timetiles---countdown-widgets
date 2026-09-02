# TimeTiles — Countdown Widgets

TimeTiles is a native Android app for tracking countdowns to the events that matter — birthdays, anniversaries, trips, deadlines — with live home-screen widgets that count down in real time.

Built entirely with Jetpack Compose and Material 3, TimeTiles supports dynamic (Material You) theming, full dark mode, and a Glance-based widget that mirrors the in-app design.

## Features

- **Countdown management** — create, edit, and delete countdowns with a custom title, emoji, and accent color.
- **Home-screen widgets** — a Glance app widget shows a live countdown for a chosen event and deep-links back into the app; refreshes automatically in the background.
- **Recurring events** — mark a countdown as yearly-repeating for birthdays and anniversaries (Premium).
- **Notifications** — get notified the moment a countdown reaches zero, checked periodically via WorkManager.
- **Onboarding flow** — a first-launch walkthrough of the app's core features.
- **Premium tiers** — weekly, monthly, yearly, and lifetime subscriptions via Google Play Billing, unlocking unlimited countdowns, the full emoji/color library, recurring events, and an ad-free experience. A separate one-time purchase removes ads only.
- **Material 3 dynamic color** — the app and widget adopt the device's wallpaper-based color palette on Android 12+, with a bundled fallback palette on older versions.
- **Accessibility** — content descriptions and state descriptions on all interactive elements, proper focus/IME handling on text input, and haptic feedback on key interactions.

## Requirements

- **Android Studio** Koala (2024.1) or newer
- **JDK** 17
- **Gradle** 8.7 (via the included wrapper)
- **Kotlin** with the Compose compiler plugin
- **minSdk** 24 (Android 7.0)
- **targetSdk / compileSdk** 34 (Android 14)
- A Google Play Console app listing with configured in-app products/subscriptions is required for billing features to function against real data (see [`BillingProducts.kt`](app/src/main/java/com/factory/timetilescountdownwidgets/billing/BillingProducts.kt) for product IDs).

## Build instructions

1. Clone the repository and open it in Android Studio, or build from the command line.
2. From the project root, build a debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install directly to a connected device or emulator:
   ```bash
   ./gradlew installDebug
   ```
4. Run unit/instrumented checks:
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

No API keys or secrets are required to build and run the app. Billing calls will no-op/fail gracefully on devices without Play Store access or without matching products configured in Play Console.

## Project structure

```
app/src/main/java/com/factory/timetilescountdownwidgets/
├── MainActivity.kt              # App entry point, splash screen, notification permission
├── TimeTilesApplication.kt      # DI-by-hand singletons, periodic WorkManager scheduling
├── navigation/                  # Compose Navigation graph (onboarding, home, add/edit, detail, paywall, settings)
├── data/                        # Room entity, DAO, database, and repository for CountdownEvent
├── ui/
│   ├── home/                    # Countdown list screen + ViewModel
│   ├── addedit/                 # Create/edit countdown screen + ViewModel
│   ├── detail/                  # Countdown detail screen + ViewModel
│   ├── onboarding/               # First-launch walkthrough
│   ├── paywall/                 # Premium upsell screen + ViewModel
│   ├── settings/                # Settings screen (premium status, restore purchases)
│   ├── components/              # Shared composables (CountdownCard, ProBadge)
│   └── theme/                   # Material 3 theme, color palette, typography
├── widget/                      # Glance app widget, its configure activity, and background refresh worker
├── notification/                # Notification channel setup and the "countdown reached zero" worker
├── billing/                     # Google Play Billing integration and premium entitlement state
├── premium/                     # Free-tier limits and premium feature descriptions
└── util/                        # Countdown time math, network checks, DataStore preferences, view-model providers
```

## Architecture

- **UI**: Jetpack Compose + Material 3, one `Screen` composable and `ViewModel` per navigation destination.
- **Persistence**: Room (`CountdownDatabase`) for countdown events, DataStore for lightweight app preferences (e.g. onboarding completion).
- **Background work**: WorkManager handles periodic reminder checks and widget refreshes.
- **Widgets**: Glance (`glance-appwidget`, `glance-material3`) renders the home-screen widget using the same theme as the main app.
- **Billing**: Google Play Billing Library (`billing-ktx`) drives subscription/one-time purchases; `PremiumManager` exposes entitlement state as `StateFlow`s consumed throughout the UI.
