# Transfer Servis — единый журнал решений и контрольных точек

**Дата сверки:** 30.10.2026. **Объект:** пассажирский Android `TransferServis` и его договорённости с `uber-v3`, `public_html`. **Исключение:** отдельный Android-проект `TransferDriver`; его задачи и PR в журнал пассажира не переносить.

**Правило ведения журнала:** это накопительный указатель на подтверждённые результаты. Исторические аудиты сохраняются как снимки на дату их проверки. Статус `merged`, успешный CI и факт реального E2E — разные утверждения и подтверждаются разными источниками. Новый пункт не отменяет старого решения без явного согласования владельца. `master`, production, выпуск/подпись APK и пользовательские заказы не изменять только на основании этого журнала.

## 1. Неизменяемые архитектурные и продуктовые решения

- Пассажирский Android: **Java + XML**, Fragment/ViewModel/LiveData (MVVM), Retrofit/OkHttp, Room, AndroidKeyStore/AES-GCM. Не переводить на Kotlin/Compose без отдельного ТЗ.
- Раздельные репозитории и области ответственности: `TransferServis` (Android пассажира), `public_html` (сайт + PHP-шлюз), `uber-v3` (Worker, D1, серверные события). `TransferDriver` — отдельный проект.
- Путь пассажирской заявки: Android/сайт → PHP `/api/calculate.php`, `/api/order.php`, `/api/order-status.php` → HMAC → Worker (`/calculate`, `/orders`, `/order-status`) → D1; Telegram — **операционное уведомление**, не источник истины.
- **D1 — source of truth** для quotes/orders. KV не использовать как авторитетное хранилище заказов. Цена/тариф quoted order читаются Worker из D1 по `quoteId`; клиентская цена не доверенная.
- Контракт `uber-v3/docs/API_CONTRACT_V1.md` помечен **FROZEN**. Ломающие изменения — только через новую версию или координированную миграцию клиентов. Для quoted order действует идемпотентность по `quoteId` + неизменному содержимому заявки.
- Поддерживаются **один локальный профиль, одна текущая сохранённая сессия заказа** и история на устройстве; несколько аккаунтов/заказов, платежи и прочие расширения не считать принятыми.
- В пассажирском Android утверждены **три тарифа: Комфорт, Бизнес, Минивэн**. **Комфорт+ исключён** из текущего мобильного интерфейса. Не переносить это решение механически на другие продукты.
- **Debug**: `ru.transferservis.app.staging`, `https://staging.transfer-servis52.ru/`, отдельная D1 `transfer-servis52-staging`; **Release**: `ru.transferservis.app`, `https://transfer-servis52.ru/`. Ни staging-токены, ни HMAC-секреты не должны попадать в Release.
- Не выполнять merge, production deployment, реальную production-заявку, подписание Release APK или его публикацию без соответствующего отдельного разрешения владельца. По решению владельца от **30.10.2026 подписание и публикация APK отложены**.
- Не повторять без необходимости ранее пройденные проверки и не запускать instrumented tests на используемом локальном AVD с сохранённой заявкой.

Документы-основания: [план Android](DEVELOPMENT_PLAN.md), [контракт API](https://github.com/lobanov0710-blip/uber-v3/blob/main/docs/API_CONTRACT_V1.md), [проверки](VALIDATION.md), [UI-01](UI_DESIGN_SYSTEM.md), [STAGING Android](STAGING_ANDROID_DEBUG.md).

## 2. Восстановленная хронология

| Дата | Источник / этап | Подтверждённая контрольная точка |
|---|---|---|
| 06–17.10 | Архитектура ARCH-01…ARCH-07, аудит от 17.10 | Утверждены quotes/orders в D1, пассажирская capability-сессия, модель активного заказа, Java/XML, замороженный API-контракт. **Аудит 17.10** описывал ещё не интегрированную в `master` Android-ветку `feature/passenger-completion`, не текущий `master`. |
| 16–20.10 | [public_html PR №1](https://github.com/lobanov0710-blip/public_html/pull/1), [№2](https://github.com/lobanov0710-blip/public_html/pull/2) | Согласование сайта/защита бронирования и мобильный UI формы; **оба merged 20.10 в `main`**. |
| 18–20.10 | [uber-v3 PR №1](https://github.com/lobanov0710-blip/uber-v3/pull/1), [№2](https://github.com/lobanov0710-blip/uber-v3/pull/2) | Staff push-device registration и доставка FCM новых заказов, **merged**. Это backend/driver-интеграция; не Android-пассажирский экран и не Telegram. |
| 21.10 | Аудиты Android / системы, Android [PR №1](https://github.com/lobanov0710-blip/TransferServis/pull/1) и [№2](https://github.com/lobanov0710-blip/TransferServis/pull/2) | Аудиты выполнялись статически на прежних SHA и не проверяли живой E2E. В рабочую **`feature/passenger-completion`**, не в `master`, вошли защита от повторной заявки при потере ответа и уведомление о pending-заявке на Home. |
| 21.10 | Android [PR №3](https://github.com/lobanov0710-blip/TransferServis/pull/3) | Ручное восстановление сохранённой заявки после потери ответа; **merged только в `feature/passenger-completion`**. |
| 21–23.10 | [uber-v3 PR №3](https://github.com/lobanov0710-blip/uber-v3/pull/3), [№4](https://github.com/lobanov0710-blip/uber-v3/pull/4), [№5](https://github.com/lobanov0710-blip/uber-v3/pull/5) | **Merged в `main`**: подавление дублирующего FCM при идемпотентном POST; запрет прошедшей даты (Москва); валидация российского телефона. |
| 21–23.10 | Android [PR №4](https://github.com/lobanov0710-blip/TransferServis/pull/4), [№5](https://github.com/lobanov0710-blip/TransferServis/pull/5), [№6](https://github.com/lobanov0710-blip/TransferServis/pull/6) | **Merged в функциональную ветку**: запрет прошлой даты, валидация телефона и исправление теста сохранения профиля. Впоследствии функциональная ветка перенесена в `master` через PR №9. |
| 23–24.10 | [uber-v3 PR №6](https://github.com/lobanov0710-blip/uber-v3/pull/6) | **Merged 24.10 в `main`**: в `APP_ENV=staging` Telegram и Firebase/FCM намеренно отключены. Production-поведение этим PR не менялось. |
| 26–28.10 | [public_html PR №5](https://github.com/lobanov0710-blip/public_html/pull/5) и Android [PR №7](https://github.com/lobanov0710-blip/TransferServis/pull/7); STAGING-06 | Изолированный HTTPS PHP Gateway **merged 28.10 в `public_html/main`**; Android Debug-конфигурация **merged в рабочую Android-ветку**. В staging пройдены HMAC, расчёт, создание, идемпотентное восстановление заказа, чтение статуса, перезапуск и потеря/возврат сети; инструментальные тесты **27/27 на отдельном Android 36.1 AVD**. Тестовый access-токен ротирован, старый отозван. Telegram/FCM намеренно не отправлялись. IP allowlist отложен владельцем. |
| 28.10 | Android [PR №10](https://github.com/lobanov0710-blip/TransferServis/pull/10), [№11](https://github.com/lobanov0710-blip/TransferServis/pull/11) | **Merged в функциональную ветку**: автоматический unsigned Release Gate и очистка IDE/диагностических артефактов перед интеграцией. |
| 28.10 | Android [PR №9](https://github.com/lobanov0710-blip/TransferServis/pull/9) | **Merged в `master`**, SHA `67b1e1b95ff243b936cd96c6d5de4a1a7bf24dbc`: пассажирская функциональность уже перенесена в основную ветку, `Hello World` больше не является актуальным описанием `master`. Интегрированный Debug/unsigned Release CI №40765068343 — success. |
| 28.10 | Android [PR №12](https://github.com/lobanov0710-blip/TransferServis/pull/12) | **Merged в `master`**: документационная фиксация интеграции и результатов CI, SHA `3b93f5e757826198ab7522230dcf6460ae91e45c`. |
| 28–30.10 | Android UI-01 [PR №13](https://github.com/lobanov0710-blip/TransferServis/pull/13) | **Merged 30.10 в `master`**, SHA `63ba7038eed8f6b60b24927155e1abf0b11bf26a`: фирменная иконка, SplashScreen, базовые цвета и стили. |
| 28–30.10 | Android UI-02 [PR №14](https://github.com/lobanov0710-blip/TransferServis/pull/14) | **Merged 30.10 в `master`**, SHA `9ca3da5b8614db6af0a738a1cfaba6815ec18ec4`: главный экран, обмен адресов, тарифы, результат расчёта. Проходили CI/visual/Espresso, включая 360dp и font scale 1.3. Устаревшая фраза `PR №14 остаётся Draft` в `UI_02_HOME_QUOTE.md` — **историческая запись, не текущий статус**. |
| 30.10 | Android RELEASE-01 [PR №16](https://github.com/lobanov0710-blip/TransferServis/pull/16) | **Merged в `master`**, SHA `a1a75f3f671b45d03bbe40af24af7faac3ba7a5d`. Проверены Android CI и Windows PowerShell preflight для **неподписанного** APK. Локальный keystore создан владельцем, **Release APK не подписывался**, по прямому решению владельца дальнейшую подпись/публикацию остановить. |
| 30.10 | [uber-v3 PR №8](https://github.com/lobanov0710-blip/uber-v3/pull/8) | **Merged в `main`**, SHA `1c4683d1c9097787df1507bcfa558725b6041571`: добавлен **opt-in** Telegram для staging при `STAGING_TELEGRAM_ENABLED=true`, отдельные staging-переменные и префикс `[ТЕСТ / STAGING]`. Кодовая возможность **не означает**, что обновлённый Worker развёрнут или флаг включён. Production не изменён. |
| 30.10 | Android PROD-SMOKE-01 [PR №17](https://github.com/lobanov0710-blip/TransferServis/pull/17) | **Open / Draft, НЕ merged**. Создан изолированный, включаемый только специальной Gradle-опцией Android emulator build `ru.transferservis.app.productiontest` для диагностики **production API без релизного keystore**. Проверки [special CI №41064297049](https://github.com/lobanov0710-blip/TransferServis/actions/runs/41064297049), [Android CI №41064297050](https://github.com/lobanov0710-blip/TransferServis/actions/runs/41064297050), [Windows №41064297026](https://github.com/lobanov0710-blip/TransferServis/actions/runs/41064297026), [UI №41064297072](https://github.com/lobanov0710-blip/TransferServis/actions/runs/41064297072) — success. **Ни установка локально, ни реальная заявка в production, ни Telegram E2E не выполнены**. Это **не новый обязательный этап проекта** и не исправление заказа; нужна отдельная приёмка владельца. |

Исторические файлы от **17/21.10** — снимки до Android PR №9; их утверждения о пустом `master` и отсутствии staging **устарели**. Повторное выполнение закрытых ARCH/интеграционных/UI задач не требуется без конкретно выявленной регрессии.

## 3. Открытые задачи и решения: не путать с выполненными

| Репозиторий | PR / вопрос | Проверенный статус на 30.10.2026 | Действие |
|---|---|---|---|
| TransferServis | [PR №17 PROD-SMOKE](https://github.com/lobanov0710-blip/TransferServis/pull/17) | **Open / Draft**, CI success | Не сливать автоматически. Это диагностическая возможность, а не обязательный релизный этап. |
| TransferServis | [PR №15 TRACKING-01](https://github.com/lobanov0710-blip/TransferServis/pull/15) | **Open / Draft** | Архитектура будущего трекинга водителя, не считать реализованной функцией текущего APK. |
| TransferServis | [PR №8 DOCS](https://github.com/lobanov0710-blip/TransferServis/pull/8) | **Open / Draft**, отдельная docs-ветка | Проверить целевую ветку отдельно; не путать с **uber-v3 PR №8**. |
| uber-v3 | [PR №7 ARCH-11.16](https://github.com/lobanov0710-blip/uber-v3/pull/7) | **Open / Draft** | Работа со статусными push-событиями водителя, не пассажирский Android UI. |
| public_html | [PR №3](https://github.com/lobanov0710-blip/public_html/pull/3) и [№4](https://github.com/lobanov0710-blip/public_html/pull/4) | **Open / Draft** | Улучшения валидации телефона/сообщений на сайте; не изменять текущий сайт без приёмки. |
| Android Release | Подпись APK и публикация | **Отложено владельцем 30.10** | Не подписывать, не публиковать, не спрашивать keystore/пароли. |
| Staging | Сетевой allowlist | **Отложен решением владельца** | Не внедрять IP ACL/.htaccess самостоятельно. |

## 4. Важный диагностический вывод: «сайт → Telegram» и «Android Debug → Telegram»

- Владелец сообщил 30.10.2026: **заявки с действующего сайта в Telegram приходят; из Android Debug — нет**. Это наблюдение владельца, не независимый просмотр Telegram-чата или production-логов.
- Текущая Android **Debug** сборка **целенаправленно отправляет только в staging**. Staging Worker по умолчанию **не посылает Telegram**. Отсутствие сообщения рабочего бота из Debug — **ожидаемое поведение конфигурации**, а не доказательство дефекта формы, PHP, HMAC, D1, Telegram-токена или Java.
- Android **Release** направлен на `https://transfer-servis52.ru/`; **неподписанный** Release APK не устанавливается. Отложенная подпись Release не означает, что старую staging-сборку следует переписать на production.
- Производственный сайт, по наблюдению владельца, отправляет уведомления. Production Worker код `tgSend()` активен без `APP_ENV=staging`. Реальная **Android → production → Telegram** цепочка **не подтверждена**.
- По замороженному API-контракту D1 сохраняет заказ **до** Telegram. Успешный HTTP/order receipt не гарантирует Telegram: ошибка отправки может быть перехвачена и зарегистрирована в журнале. Идемпотентный повтор `quoteId` не вызывает нового уведомления. Это известное ограничение канала оповещения, а не основание переписывать Android.

**Следовательно:** не нужно повторно разрабатывать Telegram-бот, создавать новые PHP API, переносить D1, менять архитектуру, пересобирать весь проект или повторять 27/27 staging-тестов. Отдельная production-проверка нужна только если владелец решит подтвердить именно незакрытый факт Android → production → Telegram. Прежде чем создавать реальный production order, согласовать тестовые данные и возможные уведомления водителям. `PR №17` можно оставить Draft или закрыть отдельным решением владельца; не считать его необходимым условием выпуска.

## 5. Что именно доказано, а что нет

**Доказано ранее:**
- Рабочий Android пассажира в `master` с 28.10; Java/XML, восстановление заявки и API-контракт; полная цепочка **staging PHP → staging Worker → staging D1**, включая 27/27 Android instrumentation на изолированном AVD.
- Android CI, визуальные/UI-проверки и сборка **неподписанного** Release APK; отдельный Windows preflight и временная сборка PROD-SMOKE проверены автоматически.
- Исходники production PHP/Worker направляют quoted orders в D1, а уведомление Telegram формируется только для созданного заказа.

**Не доказано текущими журналами:**
- Фактическая доставка **Android → production → рабочий Telegram**.
- Что новый staging-Telegram opt-in развернут и реально включён. PR №8 в `uber-v3` означает только слияние кода.
- Что Android Release был подписан, установлен/обновлён на физическом устройстве и опубликован.
- Полная production диагностика Worker/Telegram по внутренним логам и результатам доставки; она не заменяется зелёным CI.

## 6. Правило на каждый следующий этап

1. Перед новым PR сравнить задачу с этим журналом и `docs/DEVELOPMENT_PLAN.md`. Если тест/исправление уже было принято, повторять только при конкретной регрессии.
2. Для каждого действия фиксировать: **дата, репозиторий, PR/коммит, целевая ветка, тест/CI, подтверждение владельца, было ли развёртывание, остались ли вопросы**.
3. Не объединять `TransferServis` и `TransferDriver`; не путать PR с одинаковыми номерами из разных репозиториев.
4. Не изменять `master`, production runtime, секреты, staging ACL, схему D1 или подписывать APK по инициативе исторического документа.
5. Приоритет — **проверка недостающего факта**, а не новый рефакторинг того, что уже существует.

**Сверка источников:** GitHub REST history всех PR `TransferServis`, `uber-v3`, `public_html` от 30.10.2026; [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md), [VALIDATION.md](VALIDATION.md), [STAGING_ANDROID_DEBUG.md](STAGING_ANDROID_DEBUG.md), [RELEASE_SIGNING.md](RELEASE_SIGNING.md), [UI_02_HOME_QUOTE.md](UI_02_HOME_QUOTE.md), [API_CONTRACT_V1.md](https://github.com/lobanov0710-blip/uber-v3/blob/main/docs/API_CONTRACT_V1.md), технические аудиты от 17 и 21 октября.
