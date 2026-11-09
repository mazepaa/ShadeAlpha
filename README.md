# ShadeAlpha

**English** | [Русский](#русский)

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/mazepaa/ShadeAlpha/releases/latest) [![Telegram](https://img.shields.io/badge/Telegram-@mazepaa-blue?style=for-the-badge&logo=telegram)](https://t.me/mazepaa)

ShadeAlpha is an **LSPosed module** that adds transparency controls for the notification shade, recent apps, app drawer (Pixel Launcher / Launcher3), and volume panel.

The notification shade has separate controls for its upper panel area and lower notification area. The volume panel has independent controls for its background, slider track, and slider fill.

The app interface is available in English and Russian.

## Preview

<table align="center">
  <tr>
    <th align="center">100%</th>
    <th align="center">50%</th>
    <th align="center">0%</th>
  </tr>
  <tr>
    <td width="33%" align="center" valign="middle"><img src="screenshots/100_en.jpg" width="100%" alt="ShadeAlpha at 100% opacity" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/50_en.jpg" width="100%" alt="ShadeAlpha at 50% opacity" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/0_en.jpg" width="100%" alt="ShadeAlpha at 0% opacity" /></td>
  </tr>
</table>

## Features

- 🎚️ Separate transparency controls for the upper and lower parts of the notification shade.
- 🪟 Transparency controls for recent apps and the app drawer in Pixel Launcher / Launcher3.
- 🔊 Volume panel transparency, with independent settings for the background, slider track, and slider fill.
- 🎨 Material 3 interface with collapsible settings groups.
- 🌐 English and Russian interface.
- ⚙️ Three SystemUI rendering modes to accommodate different ROM implementations.

## What's new in v3.1

- Redesigned settings screen with collapsible Material 3 cards.
- Added separate transparency controls for the volume panel background, slider track, and slider fill.
- Optimized the release build, reducing the APK size.

## Requirements

- Android 12 or newer (minSdk 31).
- Root access: KernelSU, APatch, or Magisk.
- LSPosed-compatible framework (including JingMatrix / Vector).

Tested on Redmi 12 running Evolution X with Android 16 (API 36). Compatibility may vary across devices and ROMs.

## Installation

1. Download and install the latest APK from [GitHub Releases](https://github.com/mazepaa/ShadeAlpha/releases/latest).
2. Open LSPosed / Vector → Modules → ShadeAlpha and enable the module.
3. Enable the recommended scope entries: **System UI**, **ShadeAlpha**, and **Pixel Launcher** or **Launcher3** (depending on the launcher you use).
4. Open ShadeAlpha and use the restart buttons after enabling the module.
5. Adjust the sliders. **100%** preserves the original appearance; lower values make the corresponding element more transparent.

## Troubleshooting

- If the shade looks wrong, return its transparency sliders to **100%**.
- If the module does not take effect, check that it is enabled in LSPosed and that the required scope entries are selected. Restart SystemUI and the launcher.
- To reset the shade values from a root shell, run:

  ```sh
  su -c "settings delete system shade_alpha_top_percent"
  su -c "settings delete system shade_alpha_bottom_percent"
  ```

- For a bug report, include the ShadeAlpha entries from **LSPosed → Logs → Modules**, along with your Android version, ROM, and launcher.

## Known limitations

- Transparency effects may add a small amount of GPU workload.
- Very low opacity can make text and icons difficult to read, depending on the wallpaper and background.
- SystemUI and launcher implementations vary between ROMs; some devices may not support every feature.
- ROMs that replace the relevant SystemUI shade components with a fully Compose-based implementation may be incompatible.

---

# Русский

[English](#shadealpha) | **Русский**

ShadeAlpha — это **модуль LSPosed**, который добавляет настройки прозрачности шторки уведомлений, недавних приложений, списка приложений (Pixel Launcher / Launcher3) и панели громкости.

У шторки есть отдельные настройки верхней части с панелями и нижней части с уведомлениями. Для панели громкости отдельно настраивается прозрачность фона, дорожки и заполнения ползунка.

Интерфейс приложения доступен на русском и английском языках.

## Примеры

<table align="center">
  <tr>
    <th align="center">100%</th>
    <th align="center">50%</th>
    <th align="center">0%</th>
  </tr>
  <tr>
    <td width="33%" align="center" valign="middle"><img src="screenshots/100_ru.jpg" width="100%" alt="ShadeAlpha при прозрачности 100%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/50_ru.jpg" width="100%" alt="ShadeAlpha при прозрачности 50%" /></td>
    <td width="33%" align="center" valign="middle"><img src="screenshots/0_ru.jpg" width="100%" alt="ShadeAlpha при прозрачности 0%" /></td>
  </tr>
</table>

## Возможности

- 🎚️ Отдельная настройка прозрачности верхней и нижней частей шторки.
- 🪟 Настройка прозрачности недавних приложений и списка приложений в Pixel Launcher / Launcher3.
- 🔊 Настройка прозрачности панели громкости: отдельно для фона, дорожки и заполнения ползунка.
- 🎨 Интерфейс Material 3 со сворачиваемыми группами настроек.
- 🌐 Русский и английский языки.
- ⚙️ Три режима отрисовки SystemUI для совместимости с разными прошивками.

## Что нового в v3.1

- Обновлён интерфейс: настройки собраны в сворачиваемые карточки Material 3.
- Добавлены отдельные настройки прозрачности фона панели громкости, дорожки и заполнения ползунка.
- Оптимизирована сборка: размер APK уменьшен.

## Требования

- Android 12 или новее (minSdk 31).
- Root-доступ: KernelSU, APatch или Magisk.
- Совместимая с LSPosed среда (в том числе JingMatrix / Vector).

Проверено на Redmi 12 с Evolution X и Android 16 (API 36). Совместимость с другими устройствами и прошивками может отличаться.

## Установка

1. Скачай и установи последнюю версию APK из [GitHub Releases](https://github.com/mazepaa/ShadeAlpha/releases/latest).
2. Открой LSPosed / Vector → Модули → ShadeAlpha и включи модуль.
3. Отметь рекомендуемые области: **System UI**, **ShadeAlpha**, а также **Pixel Launcher** или **Launcher3** — в зависимости от используемого лаунчера.
4. Открой ShadeAlpha и воспользуйся кнопками перезапуска после включения модуля.
5. Настрой ползунки. **100%** — исходный вид системы, меньшие значения делают соответствующий элемент прозрачнее.

## Если что-то не работает

- Если шторка отображается некорректно, верни настройки её прозрачности на **100%**.
- Если модуль не работает, проверь его включение в LSPosed и выбранные области действия. Перезапусти SystemUI и лаунчер.
- Чтобы сбросить значения прозрачности шторки через root-терминал, выполни:

  ```sh
  su -c "settings delete system shade_alpha_top_percent"
  su -c "settings delete system shade_alpha_bottom_percent"
  ```

- Для сообщения об ошибке приложи строки ShadeAlpha из **LSPosed → Журналы → Модули**, а также укажи версию Android, прошивку и используемый лаунчер.

## Известные ограничения

- Эффекты прозрачности могут немного увеличивать нагрузку на GPU.
- При очень низкой прозрачности текст и значки могут плохо читаться — это зависит от обоев и фона.
- Реализации SystemUI и лаунчеров отличаются в разных прошивках, поэтому некоторые функции могут быть несовместимы с отдельными устройствами.
- Модуль может не работать на прошивках, где компоненты шторки SystemUI полностью переписаны с использованием Jetpack Compose.
