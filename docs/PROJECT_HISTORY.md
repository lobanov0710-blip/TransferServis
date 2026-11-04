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

**Предыстория (весна — сентябрь 2026, по ранее зафиксированным решениям и переписке; не полный Git-аудит):**

| Период | Контрольная точка | Точность и граница |
|---|---|---|
| Весна 2026 | Создание и улучшение сайта Transfer Servis, формы трансфера, первоначальные рекламные материалы, план теста Яндекс Директ Нижний Новгород ↔ Москва на 10 000 ₽ / 14 дней | Подтверждены обсуждение, бюджет и **требование не запускать рекламу без отдельного согласия**. Точная дата первого production deployment по имеющимся источникам **не установлена**. |
| 19.06.2026 | Контентная структура сайта: междугородние поездки, аэропорты и маршрутные страницы; тогда для сайта обсуждались классы Комфорт, Комфорт+, Бизнес, Минивэн | Это **историческая веб-линейка**, не утверждение, что `Комфорт+` должен присутствовать в Android. В Android в октябре утверждены **только три класса**. |
| Август — 04.09.2026 | Worker `uber-v3` и production PHP-прокси обсуждались и тестировались; владелец сообщил о рабочем ответе health `uber-v3-pro`. Из-за сетевой недоступности прямых `workers.dev` запросов браузера принята схема **сайт → PHP на Timeweb → Worker** | Факт браузерных наблюдений зафиксирован со слов владельца; дата внедрения каждой серверной настройки отдельно не восстановлена. Нельзя считать health доказательством Telegram/E2E. |
| 09–11.09.2026 | Упорядочение изображений главной, SEO и маршрутного контента; зафиксирована переработка 10 маршрутных страниц и sitemap | Это этап сайта, не переписывание Android/Worker. Точные SHA ранних изменений текущим журналом не установлены. |
| Сентябрь 2026 | Страница «Автоподбор»: принято **отделить её заявки от основного Telegram-бота трансфера**; брендом сайта остаётся «Transfer Servis» | Это отдельная бизнес-функция сайта. Не перенаправлять туда пассажирские заказы Android. |

**Ограничение полноты:** для весны–сентября не найден отдельный сквозной журнал со всеми SHA и датами. Не заполнять эти пробелы вымышленными коммитами или событиями. Ниже даты и слияния с октября восстановлены по актуальной истории PR GitHub.

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


## 5. Сверка 04.11.2026 — актуальные FAV/PUSH

Эта запись дополняет историческую сверку 30.10.2026. Статусы master, Draft PR, CI и production не отождествлять.

- 03.11.2026: спецификация пассажирского приложения вариант Б v0.2 объединена в master. Android FAV-01 / FAV-02 / FAV-03 объединены через PR №22, №23, №24. Последний проверенный master SHA: 43abdc8f24eb3bd77140a1b684357d9e34df2aaf. Избранные адреса и повтор завершённой поездки реализованы; отдельное сохранение пар маршрутов не подтверждено.
- 03.11.2026: PUSH-00 / PUSH-02 docs / PUSH-02A / PUSH-02B / PUSH-02C — Draft PR №25→№26→№27→№28→№29 (зависимые ветки). На HEAD PR №29 f9d6de0 Android CI и PUSH-02A CI завершились success. Никакого production FCM, Firebase SDK или регистрации пассажира пока нет.
- 04.11.2026: PUSH-02D в зависимой feature/push-02d-consent-ui от PR №29. Подготовлен раздел уведомлений на экране профиля, Java-проверка OS runtime permission для API33+ и unit/UI тесты. В реальном Debug/Release compile-time PASSENGER_PUSH_NETWORK_ENABLED=false: кнопка выключена, согласие на неработающую услугу не запрашивается и не сохраняется, канал и Firebase не активируются.
- Обязательные блокеры до rollout: отдельная безопасная серверная revoke-only регистрация/отзыв; review цепочки PHP/Worker; раздельные Firebase Android конфигурации; сервис FCM с onNewToken и revocation; E2E и отдельное одобрение владельца. Изменять compile-time флаг до закрытия блокеров нельзя.
- Состояние на 04.11: master, production, D1, TransferDriver, реальные заказы, подписание APK, публикация не изменены. Новая ветка не слита. Результаты проверки PUSH-02D указываются отдельно по фактическим GitHub Actions.


## 6. 04.11.2026 — PUSH-02D CI и серверный revoke-only контракт

- PUSH-02D PR №30, HEAD bcb13475b7a7915396b1896995772758a680af1c: GitHub Actions Android CI, unsigned Release Gate, MockWebServer, изолированный API35 consent/UI и регрессия FAV-03 завершены success, всего 7/7 check-runs. PR остаётся Draft поверх Android PUSH-02C №29, не merged.
- Backend uber-v3 PUSH-01I PR №21: Draft поверх PR №20; точный operation="revoke" выпускает passenger_push:revoke, который не принимается POST-регистрацией и допустим только для DELETE. Прежняя двухпольная форма остаётся manage-only. Backend CI на проверенном HEAD d78701de завершён success.
- PHP public_html PUSH-01I PR №9: Draft поверх PR №8. Добавлена строгая проверка операции revoke и неизменённая HMAC v2 подпись тела. PHP gateway CI на проверенном HEAD 4d67bfe завершён success. Ни один серверный PR не merged и не deployed.
- Android PUSH-02E: отдельная зависимая ветка feature/push-02e-revoke-only-grant-client поверх PR №30; prepareRevocationGrant теперь сериализует точное поле operation="revoke" без клиентского orderId. Добавлен MockWebServer HTTP body test; состояние CI для нового PR фиксировать только после завершения соответствующих GitHub Actions.
- Ограничения перед real FCM: общий лимит выдачи grant (5 за 60 с) может задержать revoke; истёкшая read-сессия не допускает выдачи revoke; lifecycle/retention и повторный opt-in не согласованы. Требуются отдельная безопасная интеграция, тестирование PHP→Worker на обновлённых кодах, Firebase setup, E2E и разрешение владельца. Feature gate PASSENGER_PUSH_NETWORK_ENABLED=false остаётся неизменным.
- Не затронуты: Android master, uber-v3 main, public_html main, production D1/Worker/PHP, реальные заказы, FCM, водительское приложение, подписание/публикация APK.

## 7. 04.11.2026 — PUSH-02E приёмка и PUSH-01J приоритет отзыва

- Android PUSH-02E PR №31 (https://github.com/lobanov0710-blip/TransferServis/pull/31), проверенный HEAD 2ab2ab153ee5b65d18eb12aa29483b41845b85e8: все 6/6 checks на этом SHA завершились success (двойные Android Debug/unsigned Release, MockWebServer, API35 consent/UI). Ранее найденный compile error ByteString.readUtf8() исправлен на ByteString.utf8(); результаты старого SHA не считать результатом финальной версии.
- Backend PUSH-01I (uber-v3 PR №21) и PHP PUSH-01I (public_html PR №9) прошли свои CI, но продолжают оставаться Draft.
- Backend PUSH-01J PR №22 (https://github.com/lobanov0710-blip/uber-v3/pull/22), проверенный HEAD 49449ffcce1fca2ed9b7c1aa02595605edbf5313: 3/3 GitHub checks success (backend, PHP HMAC→Worker→SQLite и локальный PHP HTTP/cURL→Worker Router→SQLite). Один из пяти грантов за 60 секунд зарезервирован для revoke после четырёх manage. Общий максимум 5 не изменён, новой D1 migration нет.
- Отзыв при истёкшей passenger read capability по-прежнему закрыт: нельзя выпускать grant без действующей авторизации. Local HTTP E2E проверил отказ 403. Cleanup-функция ещё не включена по расписанию; до FCM необходимы eligibility на отправке, retention, maintenance и политика восстановления.
- STAGING только при необходимости тестов; проверки прошли без удалённой staging/production D1. Production основной продукт; реальных секретов/заказов/FCM нет.
- Все указанные PR остаются Draft и не объединены. Android master, backend main, сайт main, production PHP/Worker/D1 и signed Release APK не затронуты.
- Следующий блокер: отдельный серверный lifecycle/eligibility для истёкшей регистрации, terminal order и недоступной D1; затем Firebase rollout и UX повторного согласия. Нельзя показывать статус «отключено на сервере» без подтверждённого ответа.


## 8. 04.11.2026 — PUSH-02F: подготовлен безопасный отзыв перед локальным выходом

- Android: отдельная ветка `feature/push-02f-session-exit-revoke-guard` поверх PUSH-02E PR №31. `PassengerPushSessionExitCoordinator` — только **подготовленный** Java/Retrofit протокол, к UI не подключён и не может выполнять запросы при действующем compile-time `PASSENGER_PUSH_NETWORK_ENABLED=false`.
- Порядок будущего явного выхода: проверка текущей encrypted Session → scoped `operation=revoke` grant → подтверждённый серверный DELETE `/passenger-push-device.php` → `PassengerOrderSessionStore.clearIfMatches(expected)`. Только два успешных ответа `ok=true` дают результат `REMOTE_REVOKED_AND_LOCAL_CLEARED`; неуспешные/просроченные/неверные ответы не очищают сессию и не заявляют серверный успех.
- Безопасность: при смене сессии во время сети старая операция никогда не удаляет новую; доступ в API ограничен уже существующим scope/installationId, не добавлены bearer credential storage, фоновые повторы, Firebase, установка токенов, обход истёкшего read-JWT.
- `PassengerPushSessionExitCoordinatorTest` использует MockWebServer (только localhost), покрывает disabled network, отсутствие/истечение сессии, чужой installationId, 503, отсутствие `ok=true`, гонки после grant/DELETE. Полный результат CI фиксировать по итоговому SHA и **не объявлять PASS до реального завершения**.
- Критический остаточный блокер: существующие локальные пути `ActiveOrderViewModel.clearActiveOrder()` и очистка истёкшей session **не отзывают серверный push**. Нет утверждённого logout UI, installationId store, политики offline/manual local-only exit; до их доработки FCM network flag оставлять false. Read-token после exp не допускает новый grant, серверный read-bound TTL (backend PR №26) и bounded cleanup (backend PR №25) ещё Draft.
- `docs/PUSH_02F_SESSION_EXIT_REVOKE.md` — схема, тесты и нерешённые ограничения. Production основной, STAGING только необходимые тесты. Никаких merges, D1/Worker/PHP deploy, реальных заказов, Firebase или APK signing.


## 9. 04.11.2026 — PUSH-02G: стабильный installationId без создания при отзыве

- Продолжение подготовительного Android PUSH-02F (PR №36; **все 6/6 CI success на SHA 30b58199971ba81a7310c152c5ae62b95641cf8f**, включая isolated API35). Новая ветка `feature/push-02g-installation-identity` создана от неё; изменения в производственных ветках отсутствуют.
- Добавлен локальный `PassengerPushInstallationStore` без Firebase/HTTP: `installation_id` в отдельном SharedPreferences. Случайный стабильный UUID создаётся **только** при явном opt-in и OS разрешении, а также только если `BuildConfig.PASSENGER_PUSH_NETWORK_ENABLED=true`. В текущих APK флаг по-прежнему `false`; запись не создаётся на старте, при revoke или выходе.
- `getExistingForRevocation()` read-only, отсутствие/повреждение UUID не вызывает регенерацию и блокирует сетевой revoke. После успешного server DELETE ID удаляется только если он всё ещё текущий; это предотвращает потерю новой регистрации и позволяет будущему повторному opt-in получить UUID взамен серверного tombstone. Одновременно сессию удалять только через `clearIfMatches(expected)`.
- Файл `passenger_push_installation_v1.xml` исключён в `backup_rules.xml`, `data_extraction_rules.xml` из cloud backup **и** device-transfer. Секретов, токенов, HMAC, FCM payload в нём нет.
- PUSH-02F координатор теперь имеет entry `revokeBeforeLocalExit(Session)` с чтением сохранённого ID и двумя fail-closed проверками сессии и ID до DELETE. Разделены результаты post-ack локальных ошибок: `REMOTE_REVOKED_LOCAL_CLEAR_FAILED` и `REMOTE_REVOKED_LOCAL_CLEARED_ID_RETAINED`.
- Новые Android API35 тесты проверяют долговечность, concurrent creation, malformed storage, explicit consent, запрет создания при revoke, атомарное удаление. MockWebServer проверяет server-confirmed revoke и гонки.
- Остаточный блокер: UI пока не подключён, все существующие локальные пути очистки сессии не выполняют backend revoke; реальный logout **не готов**. Не включать Firebase/FCM и сетевой feature flag до полной интеграции, серверного rollout и разрешения владельца.
- Запись относится **только** к рабочей Android ветке; окончательные статусы CI финального SHA будут проверены отдельно. Production основной продукт; STAGING только необходимые тесты. Production Worker, D1, PHP, Telegram, заказы, подписанные APK, `master`/main не изменены.
