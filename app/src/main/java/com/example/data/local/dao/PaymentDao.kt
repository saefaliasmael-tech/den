package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.Payment

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment): Int

    @Query("SELECT * FROM payments WHERE id = :paymentId")
    suspend fun getPaymentById(paymentId: Long): Payment?

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY receivedAt DESC")
    suspend fun getPaymentsForCustomer(customerId: Long): List<Payment>

    @Query("SELECT * FROM payments WHERE customerId = :customerId AND status = 'ACTIVE' ORDER BY receivedAt DESC")
    suspend fun getActivePaymentsForCustomer(customerId: Long): List<Payment>

    @Query("UPDATE payments SET status = :status WHERE id = :paymentId")
    suspend fun updateStatus(paymentId: Long, status: String): Int
}
