package com.glucoplan.foodhealth.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Какой экран показать при запуске (ТЗ 6.1, 8.4). */
class RootDecisionTest {

    private fun decide(
        ownerId: String? = null,
        profiles: Set<String> = emptySet(),
        configured: Boolean = false,
        setupDone: Boolean = false,
        initialized: Boolean = false,
        skipped: Boolean = false,
    ) = RootDecision.decide(ownerId, profiles, configured, setupDone, initialized, skipped)

    @Test
    fun `новая установка — подключение к серверу`() {
        assertThat(decide()).isEqualTo(RootState.Connect)
    }

    @Test
    fun `выбрали «без сервера» — выбор владельца`() {
        assertThat(decide(setupDone = true)).isEqualTo(RootState.NeedsOwner)
    }

    @Test
    fun `подключились — загрузка, потом выбор владельца`() {
        assertThat(decide(configured = true, setupDone = true)).isEqualTo(RootState.Download)
        assertThat(decide(configured = true, setupDone = true, initialized = true, profiles = setOf("p")))
            .isEqualTo(RootState.NeedsOwner)
    }

    @Test
    fun `загрузка оборвалась, «продолжить без загрузки» — выбор владельца`() {
        assertThat(decide(configured = true, setupDone = true, skipped = true)).isEqualTo(RootState.NeedsOwner)
    }

    @Test
    fun `обычный запуск настроенного телефона — приложение`() {
        assertThat(decide(ownerId = "p", profiles = setOf("p"), configured = true, initialized = true))
            .isEqualTo(RootState.Ready)
        // Даже если первая синхронизация ждёт выбора «объединить / заменить» — в настройках
        assertThat(decide(ownerId = "p", profiles = setOf("p"), configured = true, initialized = false))
            .isEqualTo(RootState.Ready)
    }

    @Test
    fun `телефон этапа 1а без сервера — приложение, экрана подключения нет`() {
        assertThat(decide(ownerId = "p", profiles = setOf("p"))).isEqualTo(RootState.Ready)
    }

    @Test
    fun `база очищена (8_4), сервер настроен — загрузка с сервера`() {
        assertThat(decide(ownerId = "p", profiles = emptySet(), configured = true, initialized = false))
            .isEqualTo(RootState.Download)
    }

    @Test
    fun `база очищена, сервера нет — выбор владельца, а не подключение`() {
        assertThat(decide(ownerId = "p", profiles = emptySet())).isEqualTo(RootState.NeedsOwner)
    }
}
