# ShadeAlpha

**English** | [Русский](#русский)

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/mazepaa/ShadeAlpha/releases/latest) [![Telegram](https://img.shields.io/badge/Telegram-@mazepaa-blue?style=for-the-badge&logo=telegram)](https://t.me/mazepaa)

An LSPosed module that makes the notification shade, the **recents screen** and the **app drawer** (Pixel Launcher / Launcher3) translucent. The top part (panels) and the bottom part (notifications) of the shade have separate sliders. Russian and English UI.

<table align="center">
  <tr>
    <th align="center">100%</th>
    <th align="center">50%</th>
    <th align="center">0%</th>
  </tr>
  <tr>
    <td width="33%" align="center" valign="middle"><img src="screenshots/100_en.jpg" width="100%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/50_en.jpg" width="100%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/0_en.jpg" width="100%" /></td>
  </tr>
</table>

**Download:** see [Releases](https://github.com/mazepaa/ShadeAlpha/releases).

## What's new in v2.1

- 🪟 **Recents and app drawer transparency** now actually work: the module scope includes Pixel Launcher (`com.google.android.apps.nexuslauncher`) and Launcher3 (`com.android.launcher3`).
- 🧱 App drawer: works on Android 15/16 (background drawn through `drawOnScrim*`, wrapped in a layer) and on older versions (scrim colour fields), never both at once.
- 100% = stock look, the sliders scale the original alpha instead of replacing it.
- New buttons: **Restart launcher** and **Show recents**; module status line in the app.
- Fixed: the recents/app drawer values were not re-synced to `Settings.System` on app start.

## Features (v2.0)

- 🎨 **Material You (MD3):** The app now supports dynamic colors based on your wallpaper.
- ✨ **New UI:** Smooth animations, custom sliders, and a beautiful "About" bottom sheet.
- 📳 **Haptics & Physics:** Buttons have a physical "press" effect, and some UI elements provide haptic feedback.
- 🚀 **Optimized:** Small APK size thanks to R8 compression.

## Requirements

- Root (KernelSU / APatch / Magisk) and LSPosed (JingMatrix).
- Android 12+ (minSdk 31). Tested on Redmi 12, Evolution X, Android 16 (API 36).

## Installation

1. Install `ShadeAlpha.apk`.
2. LSPosed / Vector → Modules → ShadeAlpha → enable it, scope: **System UI**, **Pixel Launcher** (or **Launcher3**) and **ShadeAlpha** (for status detection). The scope list is recommended automatically; make sure the boxes are ticked.
3. Open the ShadeAlpha app and tap **Restart SystemUI** and **Restart launcher**.
4. Move the sliders: 100% = stock look, lower = more transparent.

## How it works

- A hook inside the SystemUI process intercepts `ScrimView.onDraw` and draws the shade background in a layer with the chosen transparency: top → `scrim_behind`, bottom → `scrim_notifications`.
- **Recents:** the launcher hook post-processes the scrim colour returned by `OverviewState`, `QuickSwitchState`, `RecentsState` and `HintState` (`getWorkspaceScrimColor` / `getScrimColor`) and multiplies its alpha.
- **App drawer:** `AllAppsState` scrim is scaled the same way; the sheet itself (`ActivityAllAppsContainerView`) is either recoloured (old versions with `mScrimColor`) or drawn inside `saveLayerAlpha` (`drawOnScrim*`, Android 15/16).
- The values are stored in `Settings.System` (`shade_alpha_top_percent`, `shade_alpha_bottom_percent`, `shade_alpha_recents_percent`, `shade_alpha_appdrawer_percent`).
- **Rendering modes:** Due to different SystemUI implementations on custom ROMs, the app includes 3 rendering modes (switchable on the fly):
  - **1 (Compatibility):** Uses `saveLayerAlpha`. Maximum compatibility, but slightly higher GPU load.
  - **2 (Fast):** Uses `View.setAlpha`. Standard Android method, fast and smooth.
  - **3 (Lightest):** Multiplies `ScrimView.setViewAlpha`. Modifies color alpha directly. The lightest mode for GPU, but requires ROM support.
- The app **has no internet access**. Root is only used for two commands: `settings put system <key> <number>` and `killall com.android.systemui`.

## Troubleshooting

- Move the sliders to 100%, or run:
  `su -c "settings delete system shade_alpha_top_percent"` and `su -c "settings delete system shade_alpha_bottom_percent"`.
- Disable the module in LSPosed and restart SystemUI, or uninstall the app.
- For a bug report, send the `ShadeAlpha` lines from LSPosed → Logs → Modules:
  `hooked onDraw, methods=N` (N > 0), `Launcher loaded (...)`, `hooked ...OverviewState...`, `all apps: ... hooks`, `first hit ...`.
- No `Launcher loaded` line in the log = the launcher is not ticked in the module scope (or was not restarted).

## Known limitations

- The shade background is drawn in a separate full-screen layer, so a small extra GPU load is possible.
- At very low values the text can be hard to read on light wallpapers and apps.
- Tested only on Android 16 (Evolution X). On other ROMs the SystemUI ids and classes may differ.
- The module hooks `ScrimView`. It may not work on ROMs where SystemUI (Quick Settings) is fully rewritten using Jetpack Compose.

---

# Русский

[English](#shadealpha) | **Русский**

Модуль для LSPosed: делает полупрозрачными шторку уведомлений, **недавние приложения** и **список приложений** (Pixel Launcher / Launcher3). У шторки верхняя часть (панели) и нижняя (уведомления) настраиваются отдельными ползунками. Интерфейс на русском и английском.

<table align="center">
  <tr>
    <th align="center">100%</th>
    <th align="center">50%</th>
    <th align="center">0%</th>
  </tr>
  <tr>
    <td width="33%" align="center" valign="middle"><img src="screenshots/100_ru.jpg" width="100%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/50_ru.jpg" width="100%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/0_ru.jpg" width="100%" /></td>
  </tr>
</table>

**Скачать:** см. [Releases](https://github.com/mazepaa/ShadeAlpha/releases).

## Что нового в v2.1

- 🪟 **Прозрачность недавних и списка приложений** теперь работает: в область модуля добавлены Pixel Launcher (`com.google.android.apps.nexuslauncher`) и Launcher3 (`com.android.launcher3`).
- 🧱 Список приложений: работает на Android 15/16 (фон рисуется через `drawOnScrim*` и оборачивается в слой) и на старых версиях (поля цвета скрима), обе ветки никогда не применяются одновременно.
- 100% = как в системе, ползунки умножают исходную альфу, а не заменяют её.
- Новые кнопки: **Перезапустить лаунчер** и **Показать недавние**; строка статуса модуля.
- Исправлено: значения недавних и списка приложений не восстанавливались в `Settings.System` при запуске приложения.

## Особенности (v2.0)

- 🎨 **Material You (MD3):** Приложение поддерживает динамические цвета, подстраивающиеся под обои.
- ✨ **Новый интерфейс:** Плавные анимации, кастомные слайдеры и красивое меню "О приложении".
- 📳 **Тактильность и физика:** Кнопки имеют эффект «продавливания», а некоторые элементы вибрируют.
- 🚀 **Оптимизация:** Маленький размер APK благодаря сжатию R8.

## Требования

- Root (KernelSU / APatch / Magisk) и LSPosed (JingMatrix).
- Android 12+ (minSdk 31). Проверено на Redmi 12, Evolution X, Android 16 (API 36).

## Установка

1. Установи `ShadeAlpha.apk`.
2. LSPosed / Vector → Модули → ShadeAlpha → включить, область: **System UI**, **Pixel Launcher** (или **Launcher3**) и **ShadeAlpha** (для определения статуса). Список области предлагается автоматически, проверь, что галочки стоят.
3. Открой приложение ShadeAlpha и нажми **«Перезапустить SystemUI»** и **«Перезапустить лаунчер»**.
4. Двигай ползунки: 100% = как в системе, меньше = прозрачнее.

## Как это работает

- Хук в процессе SystemUI перехватывает `ScrimView.onDraw` и рисует фон шторки в слое с нужной прозрачностью: верх → `scrim_behind`, низ → `scrim_notifications`.
- **Недавние:** хук в лаунчере перехватывает цвет скрима, который возвращают `OverviewState`, `QuickSwitchState`, `RecentsState` и `HintState` (`getWorkspaceScrimColor` / `getScrimColor`), и умножает его альфу.
- **Список приложений:** скрим `AllAppsState` масштабируется так же; сама шторка (`ActivityAllAppsContainerView`) либо перекрашивается (старые версии с полем `mScrimColor`), либо рисуется внутри `saveLayerAlpha` (`drawOnScrim*`, Android 15/16).
- Значения хранятся в `Settings.System` (`shade_alpha_top_percent`, `shade_alpha_bottom_percent`, `shade_alpha_recents_percent`, `shade_alpha_appdrawer_percent`).
- **Режимы отрисовки:** Из-за разных реализаций SystemUI на кастомных прошивках, в приложении есть 3 режима (переключаются на лету):
  - **1 (Совместимость):** Использует `saveLayerAlpha`. Максимальная совместимость, но чуть выше нагрузка на GPU.
  - **2 (Быстрый):** Использует `View.setAlpha`. Стандартный метод Android, быстрый и плавный.
  - **3 (Самый легкий):** Умножает `ScrimView.setViewAlpha`. Меняет прозрачность цвета напрямую. Самый легкий для GPU, но требует поддержки от прошивки.
- Приложение **не имеет доступа в интернет**. Root нужен только для двух команд: `settings put system <ключ> <число>` и `killall com.android.systemui`.

## Если что-то пошло не так

- Передвинь ползунки на 100%, либо выполни:
  `su -c "settings delete system shade_alpha_top_percent"` и `su -c "settings delete system shade_alpha_bottom_percent"`.
- Выключи модуль в LSPosed и перезапусти SystemUI, либо удали приложение.
- Для багрепорта пришли строки `ShadeAlpha` из LSPosed → Журналы → Модули:
  `hooked onDraw, methods=N` (N > 0), `Launcher loaded (...)`, `hooked ...OverviewState...`, `all apps: ... hooks`, `first hit ...`.
- Нет строки `Launcher loaded` в логе: лаунчер не отмечен в области модуля (или не был перезапущен).

## Ограничения

- Фон шторки рисуется в отдельном слое на весь экран: возможна небольшая нагрузка на GPU.
- При очень низких значениях текст может плохо читаться на светлых обоях и приложениях.
- Проверено только на Android 16 (Evolution X). На других прошивках id и классы SystemUI могут отличаться.
- Модуль хукает `ScrimView`. Он может не работать на прошивках, где SystemUI (быстрые настройки) полностью переписан на Jetpack Compose.
