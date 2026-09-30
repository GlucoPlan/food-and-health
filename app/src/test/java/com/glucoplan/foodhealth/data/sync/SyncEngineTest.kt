package com.glucoplan.foodhealth.data.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.meal.DraftItem
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Синхронизация двух и более телефонов через поддельный сервер (ТЗ 6). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SyncEngineTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = FakeServer()
    private val phones = mutableListOf<Phone>()

    private suspend fun phone(name: String, connected: Boolean = true) =
        Phone(tmp.root, name, server).also { phones += it; if (connected) it.connect() }

    @After
    fun tearDown() = phones.forEach { it.close() }

    @Test
    fun `правка на одном телефоне приходит на другой`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val id = a.addProduct("Молоко")

        assertThat(a.engine.sync()).isEqualTo(SyncResult.Success(sent = 1, received = 0))
        assertThat(b.engine.sync()).isEqualTo(SyncResult.Success(sent = 0, received = 1))

        val got = b.product(id)!!
        assertThat(got.name).isEqualTo("Молоко")
        assertThat(got.kcal).isEqualTo(100.0)
        assertThat(server.rec("product", id)!!.deviceId).isEqualTo(a.prefs.deviceId())
    }

    @Test
    fun `полученное с сервера не уходит обратно`() = runTest {
        val a = phone("a")
        val b = phone("b")
        a.addProduct("Молоко")
        a.engine.sync()
        b.engine.sync()
        assertThat(b.outbox()).isEmpty()
        assertThat(a.outbox()).isEmpty()
    }

    @Test
    fun `изменение и мягкое удаление доходят`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val id = a.addProduct("Молоко")
        a.engine.sync(); b.engine.sync()

        b.rename(id, "Молоко 2,5%")
        b.engine.sync(); a.engine.sync()
        assertThat(a.product(id)!!.name).isEqualTo("Молоко 2,5%")

        a.products.delete(id)
        a.engine.sync(); b.engine.sync()
        assertThat(b.product(id)!!.deleted).isTrue()
        assertThat(b.activeNames()).isEmpty()
    }

    @Test
    fun `конфликт — побеждает пришедшая на сервер позже`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val id = a.addProduct("Молоко")
        a.engine.sync(); b.engine.sync()

        a.rename(id, "Молоко A")
        b.rename(id, "Молоко B")
        a.engine.sync()
        b.engine.sync()
        a.engine.sync()

        assertThat(a.product(id)!!.name).isEqualTo("Молоко B")
        assertThat(b.product(id)!!.name).isEqualTo("Молоко B")
    }

    @Test
    fun `порции в обе стороны`() = runTest {
        server.pageSize = 2
        val a = phone("a").apply { engine.batchSize = 2 }
        val b = phone("b")
        repeat(5) { a.addProduct("П$it") }

        assertThat((a.engine.sync() as SyncResult.Success).sent).isEqualTo(5)
        assertThat(server.records).hasSize(5)
        assertThat((b.engine.sync() as SyncResult.Success).received).isEqualTo(5)
        assertThat(b.activeNames()).containsExactly("П0", "П1", "П2", "П3", "П4")
    }

    @Test
    fun `правка во время синхронизации не теряется и не перезаписывается`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val id = a.addProduct("Молоко")
        a.engine.sync(); b.engine.sync()

        b.rename(id, "Молоко B")
        b.engine.sync()

        // Пока запрос A идёт, на A правят ту же запись; в ответе придёт версия B
        server.duringSync = { a.rename(id, "Молоко A, позже"); server.duringSync = null }
        a.engine.sync()
        assertThat(a.product(id)!!.name).isEqualTo("Молоко A, позже")
        assertThat(a.outbox().map { it.id }).containsExactly(id)

        a.engine.sync()
        b.engine.sync()
        assertThat(server.rec("product", id)!!.data["name"].toString()).contains("Молоко A, позже")
        assertThat(b.product(id)!!.name).isEqualTo("Молоко A, позже")
    }

    @Test
    fun `первый телефон с данными к пустому серверу отправляет всё без вопросов`() = runTest {
        val a = phone("a", connected = false)
        a.addProduct("Молоко")
        a.addProduct("Хлеб")
        a.connect()
        assertThat(a.engine.sync()).isEqualTo(SyncResult.Success(sent = 2, received = 0))
        assertThat(server.records).hasSize(2)
    }

    @Test
    fun `новый телефон без данных загружает всё без вопросов`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        a.engine.sync()
        val fresh = phone("fresh")
        assertThat(fresh.engine.sync()).isEqualTo(SyncResult.Success(sent = 0, received = 1))
    }

    @Test
    fun `телефон с данными к непустому серверу — вопрос, затем объединение`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        a.engine.sync()

        val c = phone("c", connected = false)
        c.addProduct("Кефир")
        c.connect()
        assertThat(c.engine.sync()).isEqualTo(SyncResult.NeedsChoice(serverRecords = 1))
        assertThat(server.records).hasSize(1)

        assertThat(c.engine.sync(FirstSyncChoice.MERGE)).isInstanceOf(SyncResult.Success::class.java)
        assertThat(c.activeNames()).containsExactly("Кефир", "Молоко")
        a.engine.sync()
        assertThat(a.activeNames()).containsExactly("Кефир", "Молоко")
        // Выбор нужен только один раз
        c.addProduct("Сыр")
        assertThat(c.engine.sync()).isInstanceOf(SyncResult.Success::class.java)
    }

    @Test
    fun `замена данными сервера удаляет данные телефона и черновик`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        a.engine.sync()

        val d = phone("d", connected = false)
        val own = d.addProduct("Кефир")
        d.draft.draft.first { it != null }
        d.draft.update { it.copy(items = listOf(DraftItem("x", MealItemType.PRODUCT, own, "100"))) }
        d.connect()

        assertThat(d.engine.sync()).isInstanceOf(SyncResult.NeedsChoice::class.java)
        assertThat(d.engine.sync(FirstSyncChoice.REPLACE)).isEqualTo(SyncResult.Success(sent = 0, received = 1))
        assertThat(d.activeNames()).containsExactly("Молоко")
        assertThat(d.product(own)).isNull()
        assertThat(d.outbox()).isEmpty()
        assertThat(d.draft.draft.value!!.items).isEmpty()
        assertThat(server.records).hasSize(1)
    }

    @Test
    fun `неверный ключ — ошибка в статусе, очередь цела`() = runTest {
        val a = phone("a")
        a.addProduct("Молоко")
        server.failWith = { SyncException.Unauthorized() }

        assertThat(a.engine.sync()).isEqualTo(SyncResult.Failed("Неверный ключ семьи"))
        assertThat(a.settings.status.first().lastError).isEqualTo("Неверный ключ семьи")
        assertThat(a.outbox()).hasSize(1)

        server.failWith = null
        assertThat(a.engine.sync()).isInstanceOf(SyncResult.Success::class.java)
        val status = a.settings.status.first()
        assertThat(status.lastError).isNull()
        assertThat(status.lastSuccessAt).isNotNull()
    }

    @Test
    fun `нет связи — ошибка, данные не страдают`() = runTest {
        val a = phone("a")
        val id = a.addProduct("Молоко")
        server.failWith = { SyncException.Network(java.io.IOException("timeout")) }
        assertThat(a.engine.sync()).isInstanceOf(SyncResult.Failed::class.java)
        assertThat(a.product(id)!!.name).isEqualTo("Молоко")
    }

    @Test
    fun `без настроенного сервера ничего не делается`() = runTest {
        val a = phone("a", connected = false)
        a.addProduct("Молоко")
        assertThat(a.engine.sync()).isEqualTo(SyncResult.NotConfigured)
        assertThat(a.outbox()).hasSize(1)
    }

    @Test
    fun `профили тоже синхронизируются`() = runTest {
        val a = phone("a")
        val b = phone("b")
        a.profiles.save(null, com.glucoplan.foodhealth.data.profile.filledProfileForm("Дочь", sd1Enabled = true))
        a.engine.sync(); b.engine.sync()
        val p = b.profiles.observeProfiles().first().single()
        assertThat(p.name).isEqualTo("Дочь")
        assertThat(p.sd1Enabled).isTrue()
    }

    @Test
    fun `рост и новые поля профиля доходят до другого телефона`() = runTest {
        val a = phone("a")
        val b = phone("b")
        a.profiles.save(
            null,
            com.glucoplan.foodhealth.data.profile.filledProfileForm(
                "Дочь", sex = com.glucoplan.foodhealth.data.profile.Sex.FEMALE,
                birthDate = java.time.LocalDate.of(2017, 10, 1),
            ).copy(waterEnabled = true, waterMlPerKg = "25"),
        )
        val id = a.profiles.observeProfiles().first().single().id
        val heights = com.glucoplan.foodhealth.data.profile.HeightRepository(a.db.heightDao(), a.prefs)
        heights.add(id, "131,5", java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30))

        a.engine.sync(); b.engine.sync()

        val p = b.profiles.get(id)!!
        assertThat(p.sex).isEqualTo(com.glucoplan.foodhealth.data.profile.Sex.FEMALE)
        assertThat(p.birthDate).isEqualTo(java.time.LocalDate.of(2017, 10, 1))
        assertThat(p.waterEnabled).isTrue()
        assertThat(p.waterMlPerKg).isEqualTo(25.0)
        val bHeights = com.glucoplan.foodhealth.data.profile.HeightRepository(b.db.heightDao(), b.prefs)
        assertThat(bHeights.observeHistory(id).first().single().heightCm).isEqualTo(131.5)
        assertThat(server.records.keys.map { it.first }).contains("height")
    }

    @Test
    fun `вес доходит до другого телефона, исправление и удаление тоже`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val aw = com.glucoplan.foodhealth.data.measure.WeightRepository(a.db.weightDao(), a.prefs)
        val bw = com.glucoplan.foodhealth.data.measure.WeightRepository(b.db.weightDao(), b.prefs)

        aw.save(null, "me", "84,2", null)
        a.engine.sync(); b.engine.sync()
        val w = bw.latest("me")!!
        assertThat(w.kg).isEqualTo(84.2)

        bw.save(w.id, "me", "83,9", w.measuredAt)
        b.engine.sync(); a.engine.sync()
        assertThat(aw.latest("me")!!.kg).isEqualTo(83.9)

        aw.delete(w.id)
        a.engine.sync(); b.engine.sync()
        assertThat(bw.latest("me")).isNull()
    }

    @Test
    fun `давление доходит до другого телефона, в том числе без пульса`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val ap = com.glucoplan.foodhealth.data.measure.BloodPressureRepository(a.db.bloodPressureDao(), a.prefs)
        val bp = com.glucoplan.foodhealth.data.measure.BloodPressureRepository(b.db.bloodPressureDao(), b.prefs)

        ap.save(null, "wife", "118", "76", "", null)
        a.engine.sync(); b.engine.sync()
        val got = bp.observeRecent("wife").first().single()
        assertThat(got.text).isEqualTo("118/76")
        assertThat(got.pulse).isNull()
    }
}
