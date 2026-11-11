# Сторонние компоненты

- [youtubedl-android](https://github.com/yausername/youtubedl-android), версия 0.18.1: GPL-3.0. Содержит нативные компоненты и ресурсы. Исходный код: https://github.com/yausername/youtubedl-android/tree/0.18.1 (если тег отсутствует, история опубликованного проекта).
- [yt-dlp](https://github.com/yt-dlp/yt-dlp): Unlicense; распространяемые сборки могут содержать компоненты под другими лицензиями. В assets включён официальный Unix zipimport-релиз [2026.10.26](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26), SHA-256 `e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3`. Архив включает [yt-dlp EJS](https://github.com/yt-dlp/ejs). yt-dlp может обновляться при работе приложения и по запросу пользователя из официального репозитория.
- [FFmpeg](https://ffmpeg.org/legal.html): LGPL/GPL в зависимости от конфигурации нативной сборки youtubedl-android. Сборочные инструкции: https://github.com/yausername/youtubedl-android/blob/master/BUILD_FFMPEG.md.
- Python: PSF License; QuickJS: MIT. Поставляются через youtubedl-android.
- AndroidX / Jetpack Compose / WorkManager: Apache-2.0.
- Coil: Apache-2.0.
- Jackson: Apache-2.0; Apache Commons IO: Apache-2.0.

Код Pocketube распространяется под GPL-3.0-or-later с учётом включаемых зависимостей. Полный текст — в файле LICENSE. При распространении бинарной сборки соблюдайте условия лицензий включённых компонентов, включая предоставление соответствующих исходников.
