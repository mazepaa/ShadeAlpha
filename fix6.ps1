# Обновляем Keys.java для надежной проверки статуса
$p1 = 'app\src\main\java\dev\shadealpha\Keys.java'
$c1 = [IO.File]::ReadAllText($p1)

# Меняем метод проверки на поиск классов Xposed в памяти приложения
$c1 = $c1 -replace 'public static boolean isModuleActive\(\) \{.*?\}', 'public static boolean isModuleActive() { try { Class.forName("de.robv.android.xposed.XposedBridge", false, Thread.currentThread().getContextClassLoader()); return true; } catch (Throwable e) { return false; } }'

# Обновляем текст статуса
$c1 = $c1 -replace 'public static String getHookStatus\(\) \{.*?\}', 'public static String getHookStatus() { return isModuleActive() ? "Активен: ScrimView.onDraw" : "Модуль не активен"; }'

[IO.File]::WriteAllText($p1, $c1, [Text.UTF8Encoding]::new($false))

Write-Host "✅ Статус LSPosed теперь определяется надежно!"