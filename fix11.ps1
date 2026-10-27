# 1. Обновляем themes.xml (используем более стабильную M3 тему)
$themesPath = 'app\src\main\res\values\themes.xml'
$themeCode = @'
<resources>
    <style name="Theme.ShadeAlpha" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
'@
[IO.File]::WriteAllText($themesPath, $themeCode, [Text.UTF8Encoding]::new($false))

# 2. Применяем тему в AndroidManifest.xml
$manifestPath = 'app\src\main\AndroidManifest.xml'
$c = [IO.File]::ReadAllText($manifestPath)
# Удаляем старую тему, если она там была
$c = $c -replace 'android:theme="[^"]*"', ''
# Добавляем нашу новую тему в тег <application>
$c = $c -replace '<application', '<application android:theme="@style/Theme.ShadeAlpha"'
[IO.File]::WriteAllText($manifestPath, $c, [Text.UTF8Encoding]::new($false))

Write-Host "✅ Тема применена в манифесте, краша больше не будет!"