package com.glucoplan.foodhealth.data.product

import com.glucoplan.foodhealth.data.db.ProductDao
import com.glucoplan.foodhealth.data.db.ProductEntity
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao,
    private val devicePrefs: DevicePrefs,
) {
    /** Все неудалённые продукты; поиск и сортировка — ProductSearch. */
    fun observeProducts(): Flow<List<Product>> =
        dao.observeActive().map { list -> list.map { it.toProduct() } }

    /** Продукт по id, в том числе удалённый: на него могут ссылаться прошлые приёмы пищи. */
    suspend fun get(id: String): Product? = dao.getById(id)?.toProduct()

    /** Создаёт ([id] = null) или сохраняет продукт. При ошибках ничего не пишет. */
    suspend fun save(id: String?, form: ProductForm): ProductValidation {
        val productId = id ?: UUID.randomUUID().toString()
        val result = ProductValidator.validate(productId, form)
        if (result !is ProductValidation.Valid) return result

        val product = result.product
        if (product.barcode != null && dao.findActiveByBarcode(product.barcode).any { it.id != productId }) {
            return ProductValidation.Invalid(mapOf(ProductField.BARCODE to "Этот штрихкод уже у другого продукта"))
        }

        val existing = id?.let { dao.getById(it) }
        dao.upsert(product.toEntity(deleted = existing?.deleted ?: false))
        return result
    }

    /** Мягкое удаление (ТЗ 4.3, 5.1): продукт пропадает из списков, но прошлые записи его видят. */
    suspend fun delete(id: String) {
        val entity = dao.getById(id) ?: return
        dao.upsert(
            entity.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId())
        )
    }

    private suspend fun Product.toEntity(deleted: Boolean) = ProductEntity(
        id = id,
        name = name,
        brand = brand,
        barcode = barcode,
        kcal = kcal,
        protein = protein,
        fat = fat,
        carbs = carbs,
        fiber = fiber,
        sugar = sugar,
        salt = salt,
        gi = gi,
        pieceWeightG = pieceWeightG,
        source = source.code,
        notes = notes,
        micro = micro,
        updatedAt = System.currentTimeMillis(),
        deleted = deleted,
        deviceId = devicePrefs.deviceId(),
    )

    private fun ProductEntity.toProduct() = Product(
        id = id,
        name = name,
        brand = brand,
        barcode = barcode,
        kcal = kcal,
        protein = protein,
        fat = fat,
        carbs = carbs,
        fiber = fiber,
        sugar = sugar,
        salt = salt,
        gi = gi,
        pieceWeightG = pieceWeightG,
        source = ProductSource.fromCode(source),
        notes = notes,
        micro = micro,
    )
}
