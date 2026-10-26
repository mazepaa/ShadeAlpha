# Тестовая сборка ShadeAlpha (три способа в одном APK)

Проверяем опасение: сработает ли «лёгкий» способ без слоя. В тестовой сборке три режима,
переключаются в приложении (блок «Режим (тест)») без пересборки.

| Режим | Что делает | Проходов GPU |
|------:|-----------|-------------|
| 0 | слой `saveLayerAlpha` (как в 1.0) | 2 |
| 1 | `View.setAlpha(f)` у самого скрима | 1 |
| 2 | умножение аргумента `ScrimView.setViewAlpha` (как «снижение альфы цвета» у Iconify) | 1 |

## Сборка
1. Распакуй архив в корень проекта с заменой (структура `app/...` совпадает), Gradle Sync.
2. Gradle → app → Tasks → build → **assembleDebug**.
   Файл: `app/build/outputs/apk/debug/ShadeAlpha-test.apk` (пакет `dev.shadealpha.test`,
   ставится рядом с релизной версией, подпись debug).

## Установка и проверка
1. Поставь ShadeAlpha-test.apk. В LSPosed **выключи релизный ShadeAlpha** (иначе они сложатся),
   включи **ShadeAlpha TEST**, область: System UI.
2. Открой Shade Alpha TEST, дай разрешение или root, поставь ползунки на 50%.
3. Для режимов 0, 1, 2 по очереди: выбрать режим → «Перезапустить SystemUI» → открыть шторку.
   Запиши: меняется ли прозрачность верха и низа.
4. Журнал LSPosed (фильтр ShadeAlpha), нужны строки:
   - `hooked onDraw, methods=N` и `hooked setViewAlpha, methods=M` (оба больше 0)
   - `first draw:scrim_behind value=...` и `first draw:scrim_notifications value=...`
   - `first setViewAlpha:scrim_behind value=...` — **если этой строки нет, setViewAlpha не вызывается
     (метод встроен), и режим 2 (способ Iconify) на этой прошивке работать не может.**

## Замер нагрузки (по каждому режиму, ползунки 50%, плюс эталон на 100%)
```
su -c "dumpsys gfxinfo com.android.systemui reset"
# 30 раз открой и закрой шторку
su -c "dumpsys gfxinfo com.android.systemui" | grep -E "Total frames|Janky|percentile"
```
Сравни `Janky frames`, `90th/95th percentile` и GPU-перцентили между режимами.
