# ShadeAlpha — Changelog

## v3.2

### 🇷🇺 Что нового

**🔐 Прозрачность экрана блокировки — новая настройка**
- Добавлена отдельная настройка прозрачности фона под кнопками экрана блокировки (PIN / пароль / графический ключ).
- Настраивается именно фон за клавиатурой: сами кнопки, цифры и текст остаются как в системе.
- 100% — фон как в системе, значения не накапливаются.

**🔊 Ползунок громкости**
- Исправлена тёмная полоса в верхней части панели громкости при прозрачности ниже 100%. Подложка ряда кнопок звонка того же цвета, что и фон панели, и перекрывала его: при полупрозрачности два слоя складывались, и в месте перекрытия появлялся тёмный «шов». Теперь подложка рисуется только над верхним краем панели — панель остаётся ровной и сохраняет скруглённый верх.
- Прозрачность корректно применяется к фону панели, подложке кнопок звонка и плавающим слайдерам.
- Отдельные ползунки прозрачности дорожки и заполнения ползунка.

**Прочее**
- Android 16 (API 36), versionCode 16, versionName 3.2.

### 🇬🇧 What's new

**🔐 Lock screen transparency — new setting**
- Added a separate transparency setting for the background under the lock screen keypad (PIN / password / pattern).
- It changes exactly that background: the buttons, digits and text stay as in stock.
- 100% means the stock background, values never accumulate.

**🔊 Volume slider**
- Fixed the dark band at the top of the volume panel below 100% transparency. The call-button row backdrop has the same colour as the panel background and overlapped it: once both became translucent the layers added up and a dark seam appeared where they met. The backdrop is now drawn only above the panel's top edge, so the panel stays even and keeps its rounded top.
- Transparency is applied correctly to the panel background, the call-button backdrop and the floating sliders.
- Separate opacity sliders for the slider track and fill.

**Other**
- Android 16 (API 36), versionCode 16, versionName 3.2.

## v3.1

### Added / Изменено
- Refreshed settings screen with collapsible Material 3 sections.
- Separate transparency controls for volume panel background, slider track and slider fill.
- Russian and English interface.

### Removed / Удалено
- PIN / password entry transparency setting and its SystemUI hook (a new one was added in 3.2).
- Experimental bouncer opacity code and related preference storage.

### Maintenance / Очистка
- Release workflow builds and signs the APK; it can be run manually and also runs on a tag push, attaching the APK to the GitHub release.
- Increased Android versionCode to 15; versionName remains 3.1.
- Kept the working notification shade, launcher and volume slider implementations unchanged.

## v3.0
- Recents and app drawer transparency.
- Module status indicator and launcher actions.
- Grouped settings sections.
