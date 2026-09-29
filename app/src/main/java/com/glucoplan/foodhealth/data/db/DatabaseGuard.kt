package com.glucoplan.foodhealth.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ТЗ 8.4: если база создана более новой версией приложения (откат через git revert),
 * приложение не падает, а очищает её. Проверка идёт до открытия Room.
 * До этапа 1б восстановить данные неоткуда, пользователь создаёт профили заново.
 */
@Singleton
class DatabaseGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _wasReset = MutableStateFlow(false)

    /** База была очищена при этом запуске — показать предупреждение. */
    val wasReset: StateFlow<Boolean> = _wasReset.asStateFlow()

    /** Вызывается до открытия базы. true — база была новее и удалена. */
    fun prepare(name: String, appVersion: Int): Boolean {
        val file = context.getDatabasePath(name)
        if (!needsReset(readVersion(file.path), appVersion)) return false
        context.deleteDatabase(name)
        _wasReset.value = true
        return true
    }

    fun dismissResetNotice() {
        _wasReset.value = false
    }

    private fun readVersion(path: String): Int? {
        if (!java.io.File(path).exists()) return null
        return try {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** [fileVersion] null — базы нет или её не удалось прочитать. */
        fun needsReset(fileVersion: Int?, appVersion: Int): Boolean =
            fileVersion != null && fileVersion > appVersion
    }
}
