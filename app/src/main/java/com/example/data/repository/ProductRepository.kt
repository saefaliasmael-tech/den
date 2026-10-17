package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.ProductDao
import com.example.data.local.dao.ProductUnitDao
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.AuditLog
import com.example.data.local.entity.Product
import com.example.data.local.entity.ProductUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class ProductWithUnits(
    val product: Product,
    val units: List<ProductUnit>
)

class ProductRepository(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val productUnitDao: ProductUnitDao,
    private val auditLogDao: AuditLogDao
) {

    private val snapshotAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ProductWithUnits::class.java)

    private fun validateUnitInput(unit: ProductUnit, isCreation: Boolean) {
        if (isCreation && unit.id != 0L) {
            throw IllegalArgumentException("New product unit must have id = 0L, found: ${unit.id}")
        }
        if (unit.id < 0L) {
            throw IllegalArgumentException("ProductUnit id cannot be negative: ${unit.id}")
        }
        if (unit.unitName.isBlank()) {
            throw IllegalArgumentException("Unit name cannot be blank")
        }
        if (unit.unitPrice < 0L) {
            throw IllegalArgumentException("Unit price cannot be negative: ${unit.unitPrice}")
        }
        if (unit.quantityScale <= 0) {
            throw IllegalArgumentException("Quantity scale must be strictly positive: ${unit.quantityScale}")
        }
    }

    /**
     * Creates a new Product and its initial selling units atomically within a Room transaction,
     * and records an AuditLog entry with the created ProductWithUnits snapshot.
     */
    suspend fun createProduct(
        product: Product,
        units: List<ProductUnit>,
        reason: String = "Create product"
    ): Result<ProductWithUnits> {
        return try {
            if (product.name.isBlank()) {
                throw IllegalArgumentException("Product name cannot be blank")
            }
            if (units.isEmpty()) {
                throw IllegalArgumentException("Product must have at least one selling unit")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }
            units.forEach { validateUnitInput(it, isCreation = true) }

            val created = db.withTransaction {
                val now = System.currentTimeMillis()
                val productToInsert = product.copy(
                    id = 0L,
                    name = product.name.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                val productId = productDao.insertProduct(productToInsert)
                if (productId <= 0L) {
                    throw IllegalStateException("Failed to insert product")
                }

                for (unit in units) {
                    val unitToInsert = unit.copy(
                        id = 0L,
                        productId = productId,
                        unitName = unit.unitName.trim(),
                        createdAt = now
                    )
                    val unitId = productUnitDao.insertUnit(unitToInsert)
                    if (unitId <= 0L) {
                        throw IllegalStateException("Failed to insert product unit: ${unit.unitName}")
                    }
                }

                val savedProduct = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Inserted product not found: $productId")
                val savedUnits = productUnitDao.getUnitsForProduct(productId)
                val snapshot = ProductWithUnits(savedProduct, savedUnits)
                val newJson = snapshotAdapter.toJson(snapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "PRODUCT",
                        entityId = productId,
                        actionType = "CREATE",
                        reason = reason,
                        oldSnapshotJson = null,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )

                snapshot
            }
            Result.success(created)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an existing Product and inserts/updates the provided selling units atomically.
     * - Distinguishes existing units (id > 0L) from new units (id == 0L).
     * - Verifies that every existing unit exists in DB and belongs to this product.
     * - Verifies that updateUnit affects 1 row.
     * - Preserves existing units that are omitted from the update list without deleting them.
     * - Records old and new snapshots of ProductWithUnits in AuditLog.
     */
    suspend fun updateProduct(
        product: Product,
        units: List<ProductUnit>,
        reason: String = "Update product"
    ): Result<ProductWithUnits> {
        return try {
            if (product.id <= 0L) {
                throw IllegalArgumentException("Product id must be positive for update: ${product.id}")
            }
            if (product.name.isBlank()) {
                throw IllegalArgumentException("Product name cannot be blank")
            }
            if (reason.isBlank()) {
                throw IllegalArgumentException("Audit reason cannot be blank")
            }

            val positiveIds = mutableSetOf<Long>()
            for (unit in units) {
                validateUnitInput(unit, isCreation = false)
                if (unit.id > 0L && !positiveIds.add(unit.id)) {
                    throw IllegalArgumentException("Duplicate unit id in update list: ${unit.id}")
                }
            }

            val updated = db.withTransaction {
                val existingProduct = productDao.getProductById(product.id)
                    ?: throw IllegalArgumentException("Product not found with id: ${product.id}")
                val existingUnits = productUnitDao.getUnitsForProduct(product.id)

                val oldSnapshot = ProductWithUnits(existingProduct, existingUnits)
                val oldJson = snapshotAdapter.toJson(oldSnapshot)

                val now = System.currentTimeMillis()
                val productToUpdate = product.copy(
                    name = product.name.trim(),
                    createdAt = existingProduct.createdAt,
                    updatedAt = now
                )
                val productAffectedRows = productDao.updateProduct(productToUpdate)
                if (productAffectedRows != 1) {
                    throw IllegalStateException("Failed to update product ${product.id}: affected rows = $productAffectedRows")
                }

                for (unit in units) {
                    if (unit.id == 0L) {
                        val unitToInsert = unit.copy(
                            id = 0L,
                            productId = product.id,
                            unitName = unit.unitName.trim(),
                            createdAt = now
                        )
                        val insertedId = productUnitDao.insertUnit(unitToInsert)
                        if (insertedId <= 0L) {
                            throw IllegalStateException("Failed to insert new unit for product ${product.id}")
                        }
                    } else {
                        val existingUnit = productUnitDao.getUnitById(unit.id)
                            ?: throw IllegalArgumentException("ProductUnit not found with id: ${unit.id}")

                        if (existingUnit.productId != product.id || (unit.productId != 0L && unit.productId != product.id)) {
                            throw IllegalArgumentException(
                                "ProductUnit ${unit.id} does not belong to product ${product.id}"
                            )
                        }

                        val unitToUpdate = unit.copy(
                            productId = product.id,
                            unitName = unit.unitName.trim(),
                            createdAt = existingUnit.createdAt
                        )
                        val unitAffectedRows = productUnitDao.updateUnit(unitToUpdate)
                        if (unitAffectedRows != 1) {
                            throw IllegalStateException("Failed to update ProductUnit ${unit.id}: affected rows = $unitAffectedRows")
                        }
                    }
                }

                val finalProduct = productDao.getProductById(product.id)
                    ?: throw IllegalStateException("Updated product not found: ${product.id}")
                val finalUnits = productUnitDao.getUnitsForProduct(product.id)
                val newSnapshot = ProductWithUnits(finalProduct, finalUnits)
                val newJson = snapshotAdapter.toJson(newSnapshot)

                auditLogDao.insertLog(
                    AuditLog(
                        entityType = "PRODUCT",
                        entityId = product.id,
                        actionType = "UPDATE",
                        reason = reason,
                        oldSnapshotJson = oldJson,
                        newSnapshotJson = newJson,
                        timestamp = now
                    )
                )

                newSnapshot
            }
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProductById(productId: Long): Product? {
        return productDao.getProductById(productId)
    }

    suspend fun getProductWithUnits(productId: Long): ProductWithUnits? {
        val product = productDao.getProductById(productId) ?: return null
        val units = productUnitDao.getUnitsForProduct(productId)
        return ProductWithUnits(product, units)
    }

    suspend fun getUnitsForProduct(productId: Long): List<ProductUnit> {
        return productUnitDao.getUnitsForProduct(productId)
    }

    suspend fun getUnitById(unitId: Long): ProductUnit? {
        return productUnitDao.getUnitById(unitId)
    }

    suspend fun getAllProducts(isActive: Boolean = true): List<Product> {
        return productDao.getAllProducts(isActive)
    }

    fun getAllProductsFlow(isActive: Boolean = true): kotlinx.coroutines.flow.Flow<List<Product>> {
        return productDao.getAllProductsFlow(isActive)
    }

    suspend fun searchProducts(query: String): List<Product> {
        return productDao.searchProducts(query)
    }
}
