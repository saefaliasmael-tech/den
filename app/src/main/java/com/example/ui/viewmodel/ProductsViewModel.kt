package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.Product
import com.example.data.local.entity.ProductUnit
import com.example.data.repository.ProductRepository
import com.example.data.repository.ProductWithUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Success(
        val products: List<ProductWithUnits>,
        val searchQuery: String = ""
    ) : ProductsUiState
    data class Error(val message: String) : ProductsUiState
}

class ProductsViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private var currentSearchQuery: String = ""

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = ProductsUiState.Loading
            productRepository.getAllProductsFlow(isActive = true)
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _uiState.value = ProductsUiState.Error(e.message ?: "Failed to load products")
                }
                .collect { productList ->
                    val withUnits = productList.map { product ->
                        val units = productRepository.getUnitsForProduct(product.id)
                        ProductWithUnits(product, units)
                    }

                    val filtered = if (currentSearchQuery.isBlank()) {
                        withUnits
                    } else {
                        withUnits.filter {
                            it.product.name.contains(currentSearchQuery, ignoreCase = true)
                        }
                    }

                    _uiState.value = ProductsUiState.Success(
                        products = filtered,
                        searchQuery = currentSearchQuery
                    )
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        currentSearchQuery = query
        viewModelScope.launch(Dispatchers.IO) {
            val all = productRepository.getAllProducts(isActive = true)
            val withUnits = all.map { product ->
                val units = productRepository.getUnitsForProduct(product.id)
                ProductWithUnits(product, units)
            }
            val filtered = if (query.isBlank()) {
                withUnits
            } else {
                withUnits.filter {
                    it.product.name.contains(query, ignoreCase = true)
                }
            }
            _uiState.value = ProductsUiState.Success(
                products = filtered,
                searchQuery = query
            )
        }
    }

    fun addProduct(
        name: String,
        unitName: String,
        unitPrice: Long,
        description: String? = null,
        onResult: (Result<ProductWithUnits>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = productRepository.createProduct(
                product = Product(
                    name = name,
                    description = description?.trim()?.takeIf { it.isNotBlank() }
                ),
                units = listOf(
                    ProductUnit(
                        productId = 0L,
                        unitName = unitName.trim(),
                        unitPrice = unitPrice,
                        quantityScale = 1,
                        isDefault = true
                    )
                )
            )
            onResult(result)
        }
    }
}
