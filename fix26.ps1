# 1. Меняем отступ с 50dp на 45dp
$p = 'app\src\main\java\dev\shadealpha\SettingsActivity.java'
$c = [IO.File]::ReadAllText($p)
$c = $c -replace 'root\.setPadding\(dp\(24\), dp\(50\), dp\(24\), dp\(32\)\);', 'root.setPadding(dp(24), dp(45), dp(24), dp(32));'
[IO.File]::WriteAllText($p, $c, [Text.UTF8Encoding]::new($false))

# 2. Поднимаем версию приложения до 1.0.1
$g = 'app\build.gradle'
$gc = [IO.File]::ReadAllText($g)
$gc = $gc -replace 'versionCode \d+', 'versionCode 4'
$gc = $gc -replace 'versionName "[^"]+"', 'versionName "1.0.1"'
[IO.File]::WriteAllText($g, $gc, [Text.UTF8Encoding]::new($false))

# 3. Переключаем GitHub Actions на сборку Release (а не Debug)
$w = '.github\workflows\build-release.yml'
if (Test-Path $w) {
    $wc = [IO.File]::ReadAllText($w)
    $wc = $wc -replace 'assembleDebug', 'assembleRelease'
    $wc = $wc -replace 'apk/debug/', 'apk/release/'
    [IO.File]::WriteAllText($w, $wc, [Text.UTF8Encoding]::new($false))
}

Write-Host "✅ Отступ 45dp, версия 1.0.1 и сборка Release настроены!"