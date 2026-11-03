# FAV-01 — локальное хранилище избранных адресов: этап реализации

**Дата:** 03.11.2026. **Ветка:** `feature/fav-01-room-address-storage`.  
**Стадия:** подготовлена реализация хранилища и тесты; GUI/выбор адресов/повтор из истории ещё не реализованы.  
**ТЗ:** [Вариант Б, v0.2 (Draft PR №21)](https://github.com/lobanov0710-blip/TransferServis/pull/21), [дизайн избранного](https://github.com/lobanov0710-blip/TransferServis/blob/docs/passenger-spec-v0-1-20261102/docs/TZ_B_FAVORITE_ROUTES_V0_1.md).

## В составе этапа

1. `FavoriteAddressEntity`: локальные `id`, `label`, `address_text`, `created_at_millis`, `updated_at_millis`; нет `orderId`, `quoteId`, цены или access token.
2. `FavoriteAddressDao`: вставка/обновление, удаление, поиск, наблюдение списка `LiveData` и чтение.
3. `TransferDatabase`: добавлена entity и DAO, версия базы **1 → 2**, явная неразрушающая `MIGRATION_1_2`. Таблица `trip_history` не пересоздаётся. Destructive fallback запрещён. Проект по-прежнему использует Room 2.8.x без SQLiteDriver и совместимую миграцию `SupportSQLiteDatabase`.
4. Инструментальные тесты `FavoriteAddressDatabaseTest`: обновление локальной v1 с историей (проверка всех сохраняемых полей), пустой v1, чистая v2 и CRUD после повторного открытия.
5. GitHub Actions `fav-01-room-migration.yml`: запуск тестов избранного **и существующих** `TripHistoryDatabaseTest` на отдельном временном Android 15 (API 35) эмуляторе. Не использует локальный AVD владельца и не выполняет production API-запросы.

## Ограничения и безопасность

- Используется существующая `transfer_servis.db`; её исключение из `backup_rules.xml` и `data_extraction_rules.xml` сохраняется. Избранные адреса не переносятся на другой телефон автоматически.
- Исторические заказы и механизмы `PassengerOrderSessionStore` / `PendingOrderSubmissionStore` не меняются.
- Это только **первый кодовый этап**: в Android нет кнопки избранного, `FavoritesRepository`, UI выбора или кнопки «Повторить», пока не будут разработаны следующие PR.
- Отдельные пары избранных маршрутов оставлены за рамками: их UX пока дополнительное предложение, а не обязательство первой версии.
- `@Database(exportSchema=false)` временно сохранён из baseline, поскольку схема v1 не экспортировалась в Git. Для миграционного теста v1 создаётся из проверенного исходного `TripHistoryEntity` вручную. Перед следующей миграцией рекомендуем экспорт schema v2 и организовать сохранение схем в Git.
- Реальные результаты CI/AVD должны быть прочитаны **после** выполнения. Наличие тестового файла или PR само по себе не означает успешного тестирования.

## Следующая итерация

1. Дождаться подтверждённого CI на этой feature-ветке и проверить, что `trip_history` сохраняется.
2. Добавить `FavoritesRepository` с нормализацией/валидаторами и фоновыми операциями, ViewModel, затем Java/XML экран управления адресами.
3. Добавить безопасный `RouteDraft`, callback «Повторить» в локальной истории, reset устаревшего quote и сохранение `hasPending()` guard.
4. Провести UI/регрессионные тесты и отдельный review перед merge.

**Merge/production deployment/подпись APK не выполнялись.**
