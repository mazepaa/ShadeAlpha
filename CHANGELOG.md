# ShadeAlpha — Changelog

## v3.5

### 🇷🇺 Что нового

**🐛 Исправлено**
- **Панель громкости больше не падает при смене режима звонка.** Подложка кнопок звонка снова остаётся настоящим `GradientDrawable`: раньше её подменяла обёртка, а системный `VolumeDialogRingerViewBinder` при анимации делает приведение к `GradientDrawable` — SystemUI падал с `ClassCastException`.
- **Подложка звонка больше не обрезается у раскрытого ряда.** Раньше она обрезалась по высоте панели на всю ширину, и когда ряд кнопок раскрывался влево, у подложки срезался край. Теперь вычитается только та область, где подложка реально перекрыта фоном панели, поэтому выступающая часть рисуется целиком со скруглением, а тёмная полоса от двойного слоя не возвращается.
- **Прозрачность фона панели громкости действительно применяется.** У `GradientDrawable` альфа хранится в цвете заливки, где `setAlpha` ничего не делает — теперь цвет масштабируется явно.
- **Убран эффект «застрявшего» фона экрана блокировки.** Если после снятия блокировки система перестаёт сообщать состояние перехода, флаг сам сбрасывается через 1.5 с, и фон продолжает перекрашиваться.
- **Слайдеры не рассинхронизируются при неудачной записи.** Если значение не удалось записать в `Settings.System`, ползунок возвращается к исходному положению, а не остаётся на новом.

**✨ Изменено**
- **У слайдеров нет всплывающих пузырьков с числом.** Процент остаётся строкой под названием и обновляется в реальном времени — лишний слой над дорожкой убран.
- **Раскрытие групп в настройках стала плавнее и предсказуемее** (карточка растёт, содержимое проявляется, 180 мс).
- **Роль скрима определяется только по id** (`scrim_behind`, `scrim_notifications`, `scrim_in_front`), без угадывания по геометрии — на планшетах и складных устройствах не перепутается верх и низ.
- **Меньше накладных расходов на кадр:** глобальный хук `onDraw` сразу отсекает вьюхи не-скримов по id, а не пересчитывает роль.
- **Все комментарии в коде переведены на короткий английский** — так модуль проще сопровождать. Русские строки интерфейса остались на месте.

**🔧 Прочее**
- `versionCode 55`, `versionName 3.5`.

### 🇬🇧 What's new

**🐛 Fixed**
- **The volume panel no longer crashes when switching the ringer mode.** The call-button backdrop stays a real `GradientDrawable`: a wrapper used to replace it, and the system's `VolumeDialogRingerViewBinder` casts that background while animating, which took SystemUI down with a `ClassCastException`.
- **The ringer backdrop is no longer cut off when the row is expanded.** It used to be clipped by the panel height across the full width, so the part sticking out beside the panel lost its edge. Now only the area actually covered by the panel background is removed, so the protruding part is drawn whole with its rounded corners, and the dark band from the double layer does not come back.
- **Panel background transparency now really applies.** A `GradientDrawable` keeps opacity in its fill colour, where `setAlpha` is a no-op - the colour is now scaled explicitly.
- **No more stuck lock-screen background.** If the system stops reporting the bouncer transition, the flag clears itself after 1.5 s and the background keeps repainting.
- **Sliders no longer desynchronise on a failed write.** When the value cannot be written to `Settings.System`, the slider rolls back to its original position instead of staying on the new one.

**✨ Changed**
- **No more floating value bubbles above the sliders.** The percentage stays as a line under the label and updates live.
- **Group opening in the settings is smoother and more predictable** (the card grows, the content fades in, 180 ms).
- **The scrim role comes from the view id only** (`scrim_behind`, `scrim_notifications`, `scrim_in_front`), no geometry guessing, so tablets and foldables cannot get top and bottom swapped.
- **Less per-frame work:** the global `onDraw` hook rejects non-scrim views by id instead of recomputing a role.
- **All code comments are short English** to make the module easier to maintain. Russian UI strings are untouched.

**🔧 Other**
- `versionCode 55`, `versionName 3.5`.

## v3.4

### 🇷🇺 Что нового

**🐛 Исправлено**
- Переключатель «Включить логи» теперь управляет **всеми** сообщениями модуля. Раньше часть строк писалась в журнал Vector напрямую, в обход переключателя. Теперь, когда переключатель выключен, модуль не пишет ни одной строки — ни при старте, ни во время работы, ни при ошибках. Если журнал нужен для багрепорта, включите переключатель и перезапустите SystemUI.
- SystemUI больше не падает при смене режима звонка (звук / вибрация / без звука): подложка кнопок звонка больше не подменяется обёрткой.

**🔧 Прочее**
- `versionCode 40`, `versionName 3.4`.

## v3.3

### 🇷🇺 Что нового

**🐛 Исправлено**
- Убран зелёный «флеш» скрима сразу после разблокировки: 500 мс после снятия keyguard скримы `scrim_behind` / `scrim_in_front` держатся в нуле, и окно закрывается досрочно, как только система догасила анимацию.
- Статус хуков больше не «залипает» после перезагрузки: рядом с маской пишется `boot_count`, после ребута показывается «статус устарел».
- Роль скрима пересчитывается при изменении размера View (поворот, планшет, складной экран).
- Настройки читаются через один кэш (`Prefs`) вместо двух параллельных.
- `su` вызывается с таймаутом 10 с, зависший запрос root больше не подвешивает поток.

**🔐 Экран блокировки**
- Прозрачность фона PIN / пароля / графического ключа теперь действительно работает: на части прошивок хук цеплялся к методам, которые система не вызывает. Фон находится по id (`keyguard_security_container`, `keyguard_bouncer_container`, `keyguard_pin_view`) и красится напрямую, всегда от исходного цвета — фон больше не тает по кадрам.
- Обе ветки хука пропускают кадры, пока бонсер закрывается, поэтому свайп не мигает.
- Сброс проверяет результат по каждому ключу; неудачная запись больше не выдаётся за успех.
- Статус «Экран блокировки» отмечается и при поиске фона по id.

**✨ Добавлено**
- **Статус хуков** в приложении: построчно «Шторка / Экран блокировки / Панель громкости / Ползунок громкости / Недавние / Список приложений» с ✅, ❌ или ❓.
- **Диагностика**: кнопка «Скопировать диагностику» в шите «О приложении» копирует в буфер версию, устройство, состояние хуков и все значения ползунков.
- **Сброс значений**: кнопка с подтверждением возвращает все настройки к значениям по умолчанию (с разрешением записывает 100 и режим по умолчанию, без него удаляет ключи через `su`) и проверяет результат по каждому ключу.
- **Запись без root**: при выданном разрешении «Изменение системных настроек» значения пишутся напрямую, `su` остаётся запасным путём.

**🔧 Прочее**
- `versionCode 27`, `versionName 3.3`.
- Сборка по тегу автоматически прикладывает APK к релизу.

### 🇬🇧 What's new

**🐛 Fixed**
- The green scrim flash right after unlocking is gone: `scrim_behind` / `scrim_in_front` stay at zero for 500 ms after the keyguard is dismissed, and the window ends as soon as the system finishes the fade.
- The hook status no longer survives a reboot as truth: the mask is stored together with `boot_count` and a different boot is reported as stale.
- The scrim role is recomputed when the view size changes (rotation, tablets, foldables).
- Settings are read through one shared cache instead of two.
- `su` calls are bounded by a 10 s timeout.

**🔐 Lock screen**
- Transparency of the PIN / password / pattern background actually works now: on some ROMs the hook targeted methods the system never calls. The background is found by id (`keyguard_security_container`, `keyguard_bouncer_container`, `keyguard_pin_view`) and painted directly, always from the original colour, so it no longer fades away frame by frame.
- Both hook paths skip frames while the bouncer fades out, so the swipe does not blink.
- Reset checks the result of every key; a failed write is no longer reported as success.
- The lock screen status is reported when the background is found by id.

**✨ Added**
- Hook status in the app: one line per hook with ✅, ❌ or ❓.
- Diagnostics: the "Copy diagnostics" button in the About sheet copies version, device, hook states and every slider value.
- Reset values: a confirmed button that returns every setting to its default (writes the default value when the system-settings permission is granted, deletes the keys through `su` otherwise) and verifies every key.
- Root-free writes: with "modify system settings" granted the values are written directly; `su` stays as the fallback.

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
