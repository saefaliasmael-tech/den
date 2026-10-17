package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ProductUnit

@Dao
interface ProductUnitDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUnit(unit: ProductUnit): Long

    @Update
    suspend fun updateUnit(unit: ProductUnit): Int

    @Query("SELECT * FROM product_units WHERE id = :unitId")
    suspend fun getUnitById(unitId: Long): ProductUnit?

    @Query("SELECT * FROM product_units WHERE productId = :productId")
    suspend fun getUnitsForProduct(productId: Long): List<ProductUnit>
}
