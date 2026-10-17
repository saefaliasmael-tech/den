package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.Customer
import com.example.data.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class CustomerWithDebt(
    val customer: Customer,
    val debt: Long
)

sealed interface CustomersUiState {
    data object Loading : CustomersUiState
    data class Success(
        val customers: List<CustomerWithDebt>,
        val searchQuery: String = ""
    ) : CustomersUiState
    data class Error(val message: String) : CustomersUiState
}

class CustomersViewModel(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CustomersUiState>(CustomersUiState.Loading)
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    private var currentSearchQuery: String = ""

    init {
        loadCustomers()
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _uiState.value = CustomersUiState.Loading
            customerRepository.getAllCustomersFlow(isArchived = false)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _uiState.value = CustomersUiState.Error(e.message ?: "Failed to load customers")
                }
                .collect { customerList ->
                    val withDebts = customerList.map { customer ->
                        val debt = try {
                            customerRepository.calculateCustomerDebt(customer.id)
                        } catch (_: Exception) {
                            0L
                        }
                        CustomerWithDebt(customer, debt)
                    }

                    val filtered = if (currentSearchQuery.isBlank()) {
                        withDebts
                    } else {
                        withDebts.filter {
                            it.customer.name.contains(currentSearchQuery, ignoreCase = true) ||
                                    it.customer.phone.contains(currentSearchQuery, ignoreCase = true)
                        }
                    }

                    _uiState.value = CustomersUiState.Success(
                        customers = filtered,
                        searchQuery = currentSearchQuery
                    )
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        currentSearchQuery = query
        val currentState = _uiState.value
        if (currentState is CustomersUiState.Success) {
            viewModelScope.launch(Dispatchers.IO) {
                val all = customerRepository.getAllCustomers(isArchived = false)
                val withDebts = all.map { customer ->
                    val debt = try {
                        customerRepository.calculateCustomerDebt(customer.id)
                    } catch (_: Exception) {
                        0L
                    }
                    CustomerWithDebt(customer, debt)
                }
                val filtered = if (query.isBlank()) {
                    withDebts
                } else {
                    withDebts.filter {
                        it.customer.name.contains(query, ignoreCase = true) ||
                                it.customer.phone.contains(query, ignoreCase = true)
                    }
                }
                _uiState.value = CustomersUiState.Success(
                    customers = filtered,
                    searchQuery = query
                )
            }
        }
    }

    fun addCustomer(
        name: String,
        phone: String,
        whatsapp: String? = null,
        notes: String? = null,
        onResult: (Result<Customer>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = customerRepository.createCustomer(
                name = name,
                phone = phone,
                whatsapp = whatsapp,
                notes = notes
            )
            onResult(result)
        }
    }
}
