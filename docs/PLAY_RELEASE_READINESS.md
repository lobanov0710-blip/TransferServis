# RELEASE-PLAY-01 — готовность Transfer Servis к Google Play

Дата: 04.11.2026. Статус: **подготовка / Draft PR, НЕ опубликовано**.
Только пассажирское Android-приложение `ru.transferservis.app`; НЕ `TransferDriver`.

## Текущая техническая конфигурация

| Параметр | Из исходников |
|---|---|
| Название | Transfer Servis |
| package/applicationId | `ru.transferservis.app` |
| Debug applicationId | `ru.transferservis.app.staging` (не загружать в Google Play) |
| versionCode / versionName | `1` / `1.0` (только если это действительно первая версия в Play Console) |
| minSdk / targetSdk / compileSdk | 24 / 37 / 37 |
| Release API | `https://transfer-servis52.ru/` |
| Push network | `PASSENGER_PUSH_NETWORK_ENABLED=false`, Firebase auto-init=false |
| Ключ | локальный `TransferServis-release.jks`, alias `transferservis_release` (подтверждено владельцем 30.10.2026) |
| Публичный upload certificate SHA-256 | `03:0A:83:6C:E4:95:45:8A:EE:39:DB:63:30:E9:94:39:A4:33:82:7C:70:47:2A:07:16:67:D2:EE:1B:56:16:89` |

**Нет доступа** к приватному JKS/паролям и к подписанному AAB. Никакой ключ не хранится в GitHub или CI. Полное соответствие опубликованному Play package/version и наличие кабинета Play Console требует проверки владельца в его аккаунте.

## Порядок слияния — до подписи

Цепочка Android PR зависит от незавершённых предшественников. Не сливать head PR №38 напрямую как замену всей цепочки без проверки ancestry, CI и отдельных разрешений.

1. SECURITY-01 [Android PR №35](https://github.com/lobanov0710-blip/TransferServis/pull/35) изолирован от PUSH-цепочки; исправление утечки Debug STAGING заголовка на другие hosts обязательно перенести в итоговую ветку, затем прогнать regression gate.
2. PUSH-00 [PR №25](https://github.com/lobanov0710-blip/TransferServis/pull/25) (base master), PUSH onboarding [№26](https://github.com/lobanov0710-blip/TransferServis/pull/26), disabled registration [№27](https://github.com/lobanov0710-blip/TransferServis/pull/27), event gate [№28](https://github.com/lobanov0710-blip/TransferServis/pull/28), status refresh [№29](https://github.com/lobanov0710-blip/TransferServis/pull/29), consent UI [№30](https://github.com/lobanov0710-blip/TransferServis/pull/30), revoke-only [№31](https://github.com/lobanov0710-blip/TransferServis/pull/31).
3. Logout safety [№36](https://github.com/lobanov0710-blip/TransferServis/pull/36), installation identity [№37](https://github.com/lobanov0710-blip/TransferServis/pull/37), explicit exit [№38](https://github.com/lobanov0710-blip/TransferServis/pull/38), затем RELEASE-PLAY-01.
4. Перед первым объединением пересчитать merge-base и проверить, какие feature-коммиты уже находятся в master: после каждого merge целевые ветки и CI должны быть актуализированы. Не заявлять, что документальные CI предыдущих PR заменяют интеграционную сборку HEAD master.
5. PR №32 (CI optimization) и №33 (specialized CI) — отдельные улучшения, не объединять автоматически в продуктовый release; PR №34 закрыт без слияния, тождественность исправления проверять относительно №35.
6. В release объединить только проверенные и **реально выключенные** будущие FCM функции. Не включать `PASSENGER_PUSH_NETWORK_ENABLED` и Firebase auto-init, не выполнять server PUSH PR merge/rollout для публикации приложения.

**Release candidate должен быть получен из объединённой и заново проверенной production/master**, а не из локального Debug/staging и не из PR head без финальной проверки. На данный момент merge не выполнен.

## AAB сборка — безопасно, без ключа

Репозиторий содержит `scripts/verify-play-aab.ps1` и отдельный GitHub Actions gate. CI запускает:

```text
./gradlew :app:bundleRelease --stacktrace
./scripts/verify-play-aab.ps1 -Mode Unsigned
```

Анализируется `app/build/outputs/bundle/release/app-release.aab`. Проверяются архив, обязательные AAB компоненты, отсутствие JAR signing entries, ожидаемая конфигурация Gradle, выключенный PUSH и SHA-256. В GitHub Actions сохраняется **только текстовый отчёт**, не signing credentials и не release bundle. Эта проверка **не является подписью и не подтверждает бинарные applicationId/SDK из manifest**: перед загрузкой нужно просмотреть собранный bundle в Android Studio или bundletool и Play Console.

## AAB подпись на Windows (только локально)

1. После всех разрешённых merge обновить чистый `master` в Android Studio / Windows, проверить `git status`, `git rev-parse HEAD` и идентичность принятому релизному коммиту. Рекомендуется отдельный каталог для подписанных артефактов **вне Git**.
2. Выполнить `.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:bundleRelease`, затем:
   ```powershell
   .\scripts\verify-play-aab.ps1 -Mode Unsigned
   ```
3. Открыть **Build → Generate Signed Bundle / APK → Android App Bundle** (НЕ APK), модуль `app`, выбрать **существующий** `TransferServis-release.jks`, alias `transferservis_release`, ввести пароли **только в Android Studio на своём компьютере**. Build variant `release`. Не создавать повторно новый JKS для этого приложения.
4. Сохранить подписанный AAB в защищённый локальный каталог за пределами Git; выполнить проверку, указав **реальный путь к итоговому подписанному bundle**:
   ```powershell
   .\scripts\verify-play-aab.ps1 -Mode Signed -BundlePath 'D:\TransferServisRelease\app-release.aab' -ExpectedCertificateSha256 '03:0A:83:6C:E4:95:45:8A:EE:39:DB:63:30:E9:94:39:A4:33:82:7C:70:47:2A:07:16:67:D2:EE:1B:56:16:89'
   ```
   Путь пример. Должно быть `PLAY_AAB_SIGNATURE_VALID=true`, `PLAY_AAB_CERTIFICATE_MATCH=true`, `PLAY_BUNDLE_PREFLIGHT=SUCCESS`. Проверить полученный `AAB_SHA256` и переносить в Play Console только этот артефакт.
5. Если package уже зарегистрирован в Play Console, использовать существующий Play upload certificate и **увеличить** `versionCode` относительно последней загруженной версии. Не заменять его просто потому, что ранее был создан новый JKS.
6. В Play Console для нового приложения выбрать Play App Signing; локальный ключ в обычной схеме становится **upload key**, отдельный **app signing key** защищён на стороне Google. Не передавать локальный JKS или пароль в чат, GitHub, e-mail или CI. Создать защищённую резервную копию keystore/паролей вне репозитория.

Справка: https://developer.android.com/studio/publish/app-signing

## Play Console — обязательные сведения до публикации

- Подтвердить, существует ли **учётная запись Play Console**, её тип (личная/организация), дату регистрации и существует ли приложение с `ru.transferservis.app`. Наличие доступа в этой сессии **не подтверждено**.
- Для личных developer accounts, созданных после **13.11.2023**, перед публичной production-публикацией требуется **закрытое тестирование с минимум 12 участниками в течение 14 последовательных дней**, затем заявка на production access. Первым безопасным каналом рекомендован **Internal testing**, не публичный Production.
- Согласовать название `Transfer Servis`, категорию Travel & Local (проверить доступную категорию в Play Console), email разработчика и контактную информацию, страны публикации, цену (бесплатное/платное), форму App content, content rating и целевую аудиторию.
- Политика конфиденциальности по адресу `https://transfer-servis52.ru/privacy.html` **есть в исходниках public_html/main**, но последняя проверенная версия описывает только веб-сайт. Нужна отдельная проверенная редакция, **прямо охватывающая Android app**, раскрывающая имя, телефон, маршрут, дату, комментарий и передачу данных заказа серверу; локальную историю, favorites и encrypted session; реальный срок хранения и процедуру запроса удаления данных. Политика должна быть доступна публично без авторизации. Без этого публикацию не выполнять. В Android Profile добавлена ссылка на известный канонический URL, её доступность в live-браузере проверить после деплоя.
- Заполнить Data safety **по фактическому поведению Android/серверов**: сведения заявки отправляются на backend, поэтому утверждать «никакие данные не собираются» нельзя. Push и Analytics, выключенные в этом релизе, не объявлять активными. Указать практику удаления данных и способ связи.
- Store listing media: реальная иконка Play **512×512 PNG** (не путать с mipmap launcher), feature graphic **1024×500 PNG/JPEG**, не менее **2 настоящих скриншотов** экрана с Android без персональных данных. Не генерировать фальшивые скриншоты несуществующих функций.
- Перед публикацией: проверка Pre-launch report, smoke на физическом Android, проверка поведения при offline, вход/выход, расчёт цены/создание заказа **только по отдельному разрешённому тестовому сценарию**, URL политики, 64-bit/target SDK compatibility, отсутствие производственных секретов и STAGING origin. Серверная D1 — production источник истины.

### Черновик карточки приложения (требует подтверждения владельца)

**Название:** Transfer Servis

**Краткое описание:** Заказ междугородних поездок, расчёт стоимости и просмотр статуса заявки.

**Основное описание:**

Transfer Servis — приложение для оформления индивидуальных междугородних поездок.

Рассчитайте маршрут и предварительную стоимость поездки, выберите доступный тариф и отправьте заявку. После оформления вы сможете просмотреть данные заявки и её текущий статус. В приложении можно сохранить контактные данные для автозаполнения, избранные адреса и посмотреть историю оформленных поездок.

Детали поездки подтверждаются диспетчером после получения заявки. Отправка заявки не равнозначна окончательному подтверждению бронирования. Функция отслеживания автомобиля на карте, чат, интернет-звонки и пассажирские push-уведомления **пока не входят в релиз**, поэтому не упоминаются как работающие.

## Финальный стоп-фактор

**Нельзя утверждать «размещено в Google Play»**, пока не выполнены в Play Console реальная загрузка **подписанного AAB**, проверка владельца/прав, создание или выбор track, заполнение обязательных деклараций и подтверждение Play release status/URL. Все внешние действия записывать в журнал с фактическим ID и ссылкой, не домысливать их.
