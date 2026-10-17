package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Customer

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer): Int

    @Query("SELECT * FROM customers WHERE id = :customerId")
    suspend fun getCustomerById(customerId: Long): Customer?

    @Query("SELECT * FROM customers WHERE isArchived = :isArchived ORDER BY name ASC")
    suspend fun getAllCustomers(isArchived: Boolean = false): List<Customer>

    @Query("SELECT * FROM customers WHERE isArchived = :isArchived ORDER BY name ASC")
    fun getAllCustomersFlow(isArchived: Boolean = false): kotlinx.coroutines.flow.Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchCustomers(query: String): List<Customer>
}
