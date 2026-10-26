
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Replace-Text {
    param([string]$File, [string]$Old, [string]$New)

    $path = Join-Path $root $File
    $content = [System.IO.File]::ReadAllText($path)

    if (!$content.Contains($Old)) {
        throw "Исходный текст не найден в $File : $Old"
    }

    $content = $content.Replace($Old, $New)
    [System.IO.File]::WriteAllText($path, $content, $utf8)
    Write-Host "OK: $File"
}

$f = 'app/src/main/java/dev/shadealpha/SettingsActivity.java'

# Общее описание прозрачности
Replace-Text $f '100% — как в системе, меньше — прозрачнее.' '100% — стандартный вид системы. Чем ниже значение, тем прозрачнее шторка.'
Replace-Text $f '100% = stock look, lower = more transparent.' '100% = default system appearance. Lower values make the shade more transparent.'

# Названия частей шторки
Replace-Text $f 'Верхняя часть (панели)' 'Верхняя часть (панели быстрых настроек)'
Replace-Text $f 'Top part (panels)' 'Top area (Quick Settings panels)'
Replace-Text $f 'Bottom part (notifications)' 'Bottom area (notifications)'

# Заголовок режимов
Replace-Text $f 'Mode (test)' 'Rendering mode'

# Режим 0
Replace-Text $f '0: Слой (макс. совместимость, выше нагрузка)' '0: Слой (лучше совместимость, выше нагрузка)'
Replace-Text $f '0: layer (saveLayerAlpha)' '0: Layer (best compatibility, higher system load)'

# Режим 1
Replace-Text $f '1: View Alpha (быстрый, плавный)' '1: View alpha (лёгкий и плавный режим)'
Replace-Text $f '1: view alpha (light)' '1: View alpha (lightweight and smooth)'

# Режим 2
Replace-Text $f '2: Scrim Alpha (самый лёгкий, мин. нагрузка)' '2: Scrim alpha (минимальная нагрузка)'
Replace-Text $f '2: scrim alpha (Iconify-like)' '2: Scrim alpha (lowest system load)'

# Подсказка после выбора режима
Replace-Text $f 'После смены режима нажми «Перезапустить SystemUI».' 'После смены режима нажми «Перезапустить SystemUI».'
Replace-Text $f 'After changing the mode tap \"Restart SystemUI\".' 'After changing the rendering mode, tap \"Restart SystemUI\".'

# Кнопки сброса
Replace-Text $f 'Сбросить (100%)' 'Сбросить до 100%'
Replace-Text $f 'Reset (100%)' 'Reset to 100%'

# Разрешение без root
Replace-Text $f 'Without root you need the \"Modify system settings\" permission.' 'Without root, the app needs the \"Modify system settings\" permission.'

# Название режима в комментарии
Replace-Text $f '// Тестовый режим' '// Режим отрисовки'

# Исправляем название именно в DEBUG-ресурсах
Replace-Text 'app/src/debug/res/values/strings.xml' 'Shade Alpha TEST' 'ShadeAlpha'

Write-Host ''
Write-Host 'Изменения внесены. Теперь проверь git diff.'
