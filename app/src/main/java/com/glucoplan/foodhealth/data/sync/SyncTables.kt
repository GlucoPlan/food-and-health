package com.glucoplan.foodhealth.data.sync

/**
 * Синхронизируемые таблицы (ТЗ 5.1). Новая таблица = строка здесь и на сервере (server/app/db.py).
 * У каждой есть id, updated_at, deleted, device_id.
 *
 * Если в будущей версии появится новая таблица, её миграция должна сбросить курсор
 * (sync_state.cursor = 0), чтобы телефон заново получил записи, пришедшие до обновления.
 */
object SyncTables {
    val ALL = listOf(
        "profile", "product", "pan", "dish", "dish_version", "dish_ingredient", "meal", "meal_item",
        // Этап 2
        "height",
    )
}
