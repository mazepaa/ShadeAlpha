# ShadeAlpha — Changelog

## v3.6

### Что нового

**Добавлено**
- Защита от бутлупа (`BootGuard`). Каждый запуск SystemUI и лаунчера записывается в файл `shadealpha_guard` в их device-protected каталоге; запуск считается «стабильным» после 60 секунд работы. Три ранних падения подряд (в пределах 10 минут) отключают установку хуков в этом пакете до тех пор, пока пользователь не нажмёт «Включить хуки снова» (нужен root) или не поставит новую сборку модуля. Любая ошибка самой защиты ведёт к обычной установке хуков, а не к блокировке. Срабатывание всегда пишется в журнал Vector.
- В приложении: баннер ⚠️ в блоке хуков, кнопка «Включить хуки снова», поле `guard=` в диагностике.

**Исправлено**
- Id скримов определяются при первой отрисовке скрима через его собственные `Resources`. Раньше значение по умолчанию (`View.NO_ID`, то есть -1) при отсутствии Application выглядело как «id найдены» и могло отбрасывать все скримы быстрым отбором.

**Изменено**
- `HookStatus.pending()` без блокировки: вызывается на пути отрисовки.
- `versionCode 57`, `versionName 3.6.0`; в `app/build.gradle` включён `buildConfig`.

### What's new

**Added**
- Boot-loop guard (`BootGuard`). Every start of SystemUI and the launcher is recorded in a `shadealpha_guard` file in their device-protected data directory; a start counts as stable after 60 seconds alive. Three early deaths in a row (within 10 minutes) stop the module from installing hooks in that package until the user taps "Re-enable hooks" (root needed) or installs a new build of the module. Any failure of the guard itself falls back to normal hooking instead of blocking. A trip is always written to the Vector log.
- In the app: a ⚠️ banner in the hooks block, a "Re-enable hooks" button, and a `guard=` field in the diagnostics.

**Fixed**
- Scrim ids are resolved on the first scrim draw through the view's own `Resources`. Before, the default value (`View.NO_ID`, i.e. -1) looked like "ids found" when no Application existed, and the fast path could reject every scrim.

**Changed**
- `HookStatus.pending()` is lock-free: it is called on the draw path.
- `versionCode 57`, `versionName 3.6.0`; `buildConfig` is enabled in `app/build.gradle`.

## v3.5 hotfix

### Что нового

**Исправлено**
- Прозрачность фона панели громкости больше не накапливается: раньше исходный цвет запоминался один раз, и после 100% → 50% → 30% панель оставалась на промежуточном значении. Теперь исходный цвет и записанное значение хранятся вместе, и ползунок каждый раз даёт ровно заданный процент.
- Шторка не пропадает на прошивках, где не находится внутренний класс ресурсов: id скримов теперь берутся из контекста приложения, а при неудаче быстрый отбор отключается с записью в лог вместо тихого пропуска всех скримов.
- Строка статуса хуков больше не пытается писать в систему на каждом кадре: без разрешения на запись — не чаще одного раза за процесс.
- Отрисовка подложки звонка безопаснее: состояние canvas сохраняется до обрезки, поэтому сбойный кадр не оставляет после себя изменённое состояние.

**Прочее**
- `versionCode 56`, `versionName 3.5.1`.
- Всё из 3.5 сохранено: смена режима звонка не роняет SystemUI, подложка звонка не обрезается, ползунок фона панели работает, всплывающих кружков с числом над слайдерами нет.

### What's new

**Fixed**
- Panel background alpha no longer stacks: the original colour used to be remembered once, so 100% -> 50% -> 30% left the panel on an intermediate value. The original colour and the written value are now tracked together, and the slider always produces exactly the chosen percentage.
- The shade no longer disappears on ROMs where the internal resource class cannot be found: the scrim ids are taken from the application context, and if that fails the fast path is switched off with a log line instead of silently skipping every scrim.
- The hook-status row no longer tries to write to the system on every frame: without write permission it is written at most once per process.
- Drawing the ringer backdrop is safer: the canvas state is saved before the clip, so a failing frame cannot leave modified state behind.

**Other**
- `versionCode 56`, `versionName 3.5.1`.
- Everything from 3.5 stays: the ringer-mode switch does not crash SystemUI, the backdrop is not cut off, the panel background slider works, and the value bubbles are not shown.

## v3.5

### Что нового

**Исправлено**
- SystemUI больше не падает при смене режима звонка (звук / вибрация / без звука). Подложка кнопок звонка снова остаётся настоящим `GradientDrawable`: обёртка над ним ломала системное приведение типа при анимации.
- Подложка звонка больше не обрезается у раскрытого ряда кнопок. Вычитается только та область, где подложку перекрывает фон панели, поэтому выступающая часть рисуется целиком, со скруглением, и тёмная полоса от двойного слоя не возвращается.
- Прозрачность фона панели громкости действительно применяется: у `GradientDrawable` прозрачность хранится в цвете заливки, и прежний вызов её не менял.
- Фон экрана блокировки больше не «застревает», если система перестала сообщать состояние перехода: флаг сбрасывается сам через 1.5 с.
- Если значение ползунка не удалось записать в систему, ползунок возвращается к прежнему положению вместо рассинхрона.

**Изменено**
- У слайдеров больше нет всплывающих пузырьков с числом: процент показывается строкой под названием и обновляется на лету.
- Роль скрима определяется по id (`scrim_behind`, `scrim_notifications`, `scrim_in_front`) без угадывания по геометрии — на планшетах и складных экранах верх и низ не перепутаются.
- Меньше работы на кадр: глобальный хук `onDraw` сразу отбрасывает вьюхи, которые не являются скримами.

**Прочее**
- `versionCode 55`, `versionName 3.5`.
- Комментарии в коде переведены на английский, русские строки интерфейса сохранены.

### What's new

**Fixed**
- SystemUI no longer crashes when switching the ringer mode (sound / vibration / silent). The call-button backdrop stays a real `GradientDrawable`; the wrapper around it broke the system's own cast during the animation.
- The ringer backdrop is no longer cut off when the button row is expanded. Only the area covered by the panel background is removed, so the protruding part is drawn whole with its rounded corners, and the dark band from the double layer does not come back.
- Panel background transparency really applies now: a `GradientDrawable` keeps its opacity in the fill colour, which the previous call never changed.
- The lock-screen background no longer gets stuck when the system stops reporting the transition state: the flag clears itself after 1.5 s.
- When a slider value cannot be written to the system, the slider rolls back to its previous position instead of drifting out of sync.

**Changed**
- Sliders no longer show floating value bubbles: the percentage is a line under the label, updated live.
- The scrim role comes from the view id (`scrim_behind`, `scrim_notifications`, `scrim_in_front`) with no geometry guessing, so tablets and foldables cannot swap top and bottom.
- Less per-frame work: the global `onDraw` hook rejects views that are not scrims right away.

**Other**
- `versionCode 55`, `versionName 3.5`.
- Code comments are in English; the Russian UI strings are unchanged.

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
- Убран «флеш» скрима сразу после разблокировки: 500 мс после снятия keyguard скримы `scrim_behind` / `scrim_in_front` держатся в нуле, и окно закрывается досрочно, как только система догасила анимацию.
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
- The scrim flash right after unlocking is gone: `scrim_behind` / `scrim_in_front` stay at zero for 500 ms after the keyguard is dismissed, and the window ends as soon as the system finishes the fade.
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
