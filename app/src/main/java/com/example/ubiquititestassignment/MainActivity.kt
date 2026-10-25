package com.example.ubiquititestassignment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ubiquititestassignment.ui.catalog.CatalogRoute
import com.example.ubiquititestassignment.ui.theme.UbiquitiTestAssignmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UbiquitiTestAssignmentTheme {
                CatalogRoute()
            }
        }
    }
}
