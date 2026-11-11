# Pocketube

Android-приложение для скачивания видео YouTube и аудиодорожек MP3.

- Две вкладки: добавление ссылки и медиатека.
- Остановка, продолжение и удаление загрузок.
- Встроенный плеер, мини-плеер и полный экран с поддержкой поворота.
- Плейлисты, сортировка и фоновое прослушивание.
- Анимированный фон аудиоплеера и громкость до 200%.

## Установка

Android 8.0+, ARM64. Ссылка на актуальный APK — в [релизах](https://github.com/Vanchegs/Pocketube/releases/latest). Обновление с той же подписью сохраняет медиатеку.

## Сборка

JDK 17 и Android SDK 35. Укажите SDK в `local.properties` или `ANDROID_HOME`.

```sh
./gradlew testDebugUnitTest assembleDebug -PtargetAbi=arm64-v8a
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Для обновлений используйте постоянный ключ подписи.

Доступность загрузок зависит от YouTube и сети. Усиление выше 100% может искажать звук. Удаление приложения удаляет его локальные файлы.

Лицензия приложения: [MIT](LICENSE). Лицензии включённых компонентов: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Проверки: [VALIDATION.md](VALIDATION.md).
