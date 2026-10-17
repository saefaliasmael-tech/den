package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CustomerRepository
import com.example.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class TopDebtorItem(
    val customerId: Long,
    val name: String,
    val phone: String,
    val debt: Long,
    val percentageOfTotal: Float
)

data class HomeDashboardState(
    val totalDebt: Long = 0L,
    val totalCustomers: Int = 0,
    val debtorsCount: Int = 0,
    val clearedCustomersCount: Int = 0,
    val totalProducts: Int = 0,
    val topDebtors: List<TopDebtorItem> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val customerRepository: CustomerRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(HomeDashboardState())
    val dashboardState: StateFlow<HomeDashboardState> = _dashboardState.asStateFlow()

    init {
        observeDashboard()
    }

    private fun observeDashboard() {
        viewModelScope.launch {
            combine(
                customerRepository.getAllCustomersFlow(isArchived = false),
                productRepository.getAllProductsFlow(isActive = true)
            ) { customers, products ->
                var debtSum = 0L
                val withDebts = customers.map { customer ->
                    val debt = try {
                        customerRepository.calculateCustomerDebt(customer.id)
                    } catch (_: Exception) {
                        0L
                    }
                    try {
                        debtSum = Math.addExact(debtSum, debt)
                    } catch (_: ArithmeticException) {
                        debtSum = Long.MAX_VALUE
                    }
                    CustomerWithDebt(customer, debt)
                }

                val debtors = withDebts.filter { it.debt > 0L }
                val cleared = withDebts.filter { it.debt == 0L }

                val topList = debtors.sortedByDescending { it.debt }
                    .take(4)
                    .map { item ->
                        val ratio = if (debtSum > 0L) {
                            (item.debt.toDouble() / debtSum.toDouble()).toFloat()
                        } else {
                            0f
                        }
                        TopDebtorItem(
                            customerId = item.customer.id,
                            name = item.customer.name,
                            phone = item.customer.phone,
                            debt = item.debt,
                            percentageOfTotal = (ratio * 100f).coerceIn(0f, 100f)
                        )
                    }

                HomeDashboardState(
                    totalDebt = debtSum,
                    totalCustomers = customers.size,
                    debtorsCount = debtors.size,
                    clearedCustomersCount = cleared.size,
                    totalProducts = products.size,
                    topDebtors = topList,
                    isLoading = false
                )
            }.flowOn(Dispatchers.IO).collect { newState ->
                _dashboardState.value = newState
            }
        }
    }
}
