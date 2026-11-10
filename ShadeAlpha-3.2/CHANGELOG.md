# ShadeAlpha — Changelog

## v3.2

### Fixed / Исправлено
- Notification shade: both top (Quick Settings) and bottom (notifications) areas now correctly receive independent transparency. Improved ScrimView role detection (resource id + geometry fallback) so only one half no longer “sticks”.
- Better coexistence of shade transparency with the lock-screen / bouncer.

### Added / Добавлено
- Lock screen (PIN / password / pattern) transparency control restored (`shade_alpha_bouncer_percent`).
- BouncerHook: KeyguardSecurityContainer background, ScrimController state tracking, unlock fade handling.

### Maintenance / Очистка
- Removed leftover GitHub Actions workflow.
- versionCode 16, versionName 3.2.
- Code cleanup.

## v3.1

### Added / Изменено
- Refreshed settings screen with collapsible Material 3 sections.
- Separate transparency controls for volume panel background, slider track and slider fill.
- Russian and English interface.

### Removed / Удалено
- PIN / password entry transparency setting and its SystemUI hook (restored in 3.2).
- Experimental bouncer opacity code and related preference storage.

### Maintenance / Очистка
- Removed the GitHub Actions release workflow as requested.
- Increased Android versionCode to 15; versionName remains 3.1.
- Kept the working notification shade, launcher and volume slider implementations unchanged.

## v3.0
- Recents and app drawer transparency.
- Module status indicator and launcher actions.
- Grouped settings sections.
