# RELEASE-01 — подготовка подписанного APK (пассажирский Transfer Servis)

**Статус на 29.10.2026:** подготовка процедуры; подписанный APK не создавался. Только для Android-приложения `ru.transferservis.app`, репозиторий `TransferServis`, НЕ `TransferDriver`.

## Проверенное состояние master

| Параметр | Значение |
|---|---|
| applicationId Release | `ru.transferservis.app` |
| applicationId Debug | `ru.transferservis.app.staging` |
| minSdk / targetSdk | 24 / 37 |
| versionCode / versionName | 1 / 1.0 |
| Release API | `https://transfer-servis52.ru/` |
| Схема CI | Unit + Lint + сборка **неподписанного** Release APK; подтверждается отсутствие действительной подписи |
| Release артефакт | `app/build/outputs/apk/release/app-release-unsigned.apk` |

Подписание, публикация, создание production-заказов и изменение серверов не выполняются. UI-01 PR №13 — Ready for review; UI-02 PR №14 — Draft и зависит от UI-01. **Подписывать окончательный APK только после согласованных UI-PR, повторной проверки интегрированного master и фиксации версии**. Для будущих Яндекс Карт (TRACKING-01/PR №15) отдельные лицензия/совместимость, они не входят в этот APK.

## Ключ и безопасность

1. **Проверить, не существует ли уже production keystore** для `ru.transferservis.app`. Если ранее распространялась production-сборка, APK для обновления должен использовать совместимую прежнюю подпись. Нельзя создавать новый ключ и объявлять его прежним.
2. Если это **первая** опубликованная версия и ключа нет — создать **один постоянный ключ владельца** в Android Studio, `Build > Generate Signed Bundle / APK > APK > Create new`. Указать надёжный пароль, alias, срок действия **не менее 25 лет**, сохранить `.jks` ВНЕ папки Git и папок автоматической синхронизации без отдельной защиты.
3. Сохранить защищённые офлайн-резервные копии ключа и паролей отдельно. Потеря ключа самостоятельной дистрибуции может сделать дальнейшие обновления невозможными. Не пересылать `.jks`, пароли или скриншоты секретов в чат, Git, Actions, issue и Pull Request.
4. Записать и сверить **публичный SHA-256 отпечаток сертификата** подписанного APK; его можно сравнивать без передачи приватного ключа.
5. Если предполагается **Google Play**, сначала выбрать стратегию Play App Signing и подписываемый **AAB**; это отдельный процесс. Для передачи APK напрямую используется локальная подпись APK.

См. официальную документацию:
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/build/building-cmdline
- https://developer.android.com/tools/apksigner

## Порядок работы на Windows (Android Studio)

1. Обновить локальный `master` только после согласованных слияний UI-01/UI-02. Проверить `git status` и `git rev-parse HEAD`; рабочая копия без незакоммиченных изменений.
2. Убедиться, что Java/JDK и Android SDK соответствуют конфигурации проекта; выполнить:
   ```powershell
   .\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
   ```
3. **Проверить неподписанный APK**, его идентичность, версию и выравнивание:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release-apk.ps1 -Mode Unsigned
   ```
4. В Android Studio открыть **Build > Generate Signed Bundle / APK**, выбрать **APK**, модуль `app`, существующий production keystore (или создать новый только при первом выпуске), alias и пароли. Выбрать build variant **release**, подпись APK Signature Scheme v2 (для API 24+) и доступные совместимые схемы. **Debug** не выбирать.
5. Сохранить итоговый APK и SHA-256 за пределами репозитория. Проверить командой:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release-apk.ps1 -Mode Signed -ApkPath 'D:\TransferServisRelease\TransferServis-1.0.apk'
   ```
   Пример пути заменить фактическим. Скрипт использует локальные `aapt.exe`, `zipalign.exe`, `apksigner.bat`; при нестандартном расположении Android SDK передать `-SdkRoot`.
6. При **обновлении** установленного production-приложения дополнительно использовать `-ExpectedCertificateSha256 '<previous-release-certificate-sha256>'` и убедиться, что `versionCode` увеличен по сравнению с опубликованной версией; перед запуском скрипта передать также `-ExpectedVersionCode` и `-ExpectedVersionName`. Для **первого** выпуска значения `1`/`1.0` допустимы после согласования.
7. Проверить установку/запуск **на отдельном физическом Android-устройстве** с учётом необходимости сохранить существующую сессию пользователя. Не удалять рабочую установку и данные без резервной копии. Проверить иконку, SplashScreen, интерфейс, системные панели, доступность экранов и офлайн-режим. Тестовые POST в **production** без отдельного разрешения не отправлять.

### Проверки APK

`scripts/verify-release-apk.ps1` **только читает APK**, не создаёт ключ, не подписывает, не загружает файл и не вызывает сервер. Проверяет:
- `applicationId=ru.transferservis.app` — не допускает случайно раздавать `ru.transferservis.app.staging`.
- `versionCode`/`versionName`, совпадение с ожидаемыми.
- `zipalign -c -v 4`.
- `apksigner verify`: `Unsigned` — подпись отсутствует, `Signed` — подпись действительна.
- Вывод SHA-256 APK; для `Signed` — SHA-256 сертификата и необязательное сравнение с ожидаемым.

Это **локальный Windows PowerShell-скрипт, требующий фактической проверки в среде Android SDK владельца**; зелёный GitHub CI сам по себе не подтверждает его выполнение. Рабочий пароль никогда не передавать через аргументы командной строки.

## Release gate перед первым распространением

- [ ] PR №13 (UI-01) и PR №14 (UI-02) проверены и **слиты только с отдельного разрешения владельца**; повторные CI на интегрированном `master`.
- [ ] `versionCode`, `versionName`, package ID, icon и название утверждены.
- [ ] Согласован канал распространения: подписанный APK напрямую или AAB для Play Store.
- [ ] Проверено, есть ли прежний production keystore; при обновлении сертификат совпадает.
- [ ] Приватный keystore и пароли хранятся вне Git; есть резервная копия.
- [ ] Неподписанный Release и подписанный APK прошли независимый preflight.
- [ ] На **физическом устройстве** подтверждены установка, первый запуск и обновление (если применимо) без потери профиля/заказов.
- [ ] Production-сценарии проверены только после отдельного согласования; релизная подпись не означает разрешения публиковать APK.

**Этот PR специально не меняет `app/build.gradle.kts`, gradle signingConfig, CI и Release API**: локальное подписание с помощью Android Studio не должно случайно создавать подписанный APK в обычном CI.
