package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Product

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product): Int

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Long): Product?

    @Query("SELECT * FROM products WHERE isActive = :isActive ORDER BY name ASC")
    suspend fun getAllProducts(isActive: Boolean = true): List<Product>

    @Query("SELECT * FROM products WHERE isActive = :isActive ORDER BY name ASC")
    fun getAllProductsFlow(isActive: Boolean = true): kotlinx.coroutines.flow.Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchProducts(query: String): List<Product>
}
