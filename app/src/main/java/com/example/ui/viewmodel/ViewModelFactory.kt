package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.CustomerRepository
import com.example.data.repository.InvoiceRepository
import com.example.data.repository.PaymentRepository
import com.example.data.repository.ProductRepository

class ViewModelFactory(
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(customerRepository, productRepository) as T
            }
            modelClass.isAssignableFrom(CustomersViewModel::class.java) -> {
                CustomersViewModel(customerRepository) as T
            }
            modelClass.isAssignableFrom(ProductsViewModel::class.java) -> {
                ProductsViewModel(productRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
