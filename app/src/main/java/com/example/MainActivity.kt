package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.MainAppScaffold
import com.example.ui.theme.DuyooniProTheme
import com.example.ui.viewmodel.CustomersViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ProductsViewModel
import com.example.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DuyooniApp
        val factory = ViewModelFactory(
            customerRepository = app.customerRepository,
            productRepository = app.productRepository,
            invoiceRepository = app.invoiceRepository,
            paymentRepository = app.paymentRepository
        )
        val viewModelProvider = ViewModelProvider(this, factory)
        val homeViewModel = viewModelProvider[HomeViewModel::class.java]
        val customersViewModel = viewModelProvider[CustomersViewModel::class.java]
        val productsViewModel = viewModelProvider[ProductsViewModel::class.java]

        setContent {
            DuyooniProTheme {
                MainAppScaffold(
                    homeViewModel = homeViewModel,
                    customersViewModel = customersViewModel,
                    productsViewModel = productsViewModel
                )
            }
        }
    }
}
