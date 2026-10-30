package com.tooheavy.app.ui.nutrition

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tooheavy.app.data.NutritionRepo
import com.tooheavy.app.data.OpenFoodFacts
import com.tooheavy.app.ui.Routes
import com.tooheavy.app.ui.common.AppTopBar
import com.tooheavy.app.ui.common.GlassCard

/** Replaces the barcode screen on the back stack with [route]. */
private fun barcodeContinue(nav: NavController, route: String) {
    nav.navigate(route) { popUpTo(Routes.BARCODE_PATTERN) { inclusive = true } }
}

/**
 * Resolves a scanned barcode: a food already stored on the phone goes straight to the amount screen; otherwise the
 * code is looked up on Open Food Facts and saved locally, or the custom food editor is opened prefilled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeLookupScreen(nav: NavController, code: String, meal: String) {
    val context = LocalContext.current
    val barcode = code.trim()
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(barcode, attempt) {
        error = null
        val local = NutritionRepo.foodByBarcode(barcode)
        if (local != null) {
            barcodeContinue(nav, Routes.foodAmount(local.key, meal))
            return@LaunchedEffect
        }
        when (val result = OpenFoodFacts.lookup(barcode)) {
            is OpenFoodFacts.Result.Found -> {
                if (result.complete) {
                    val saved = NutritionRepo.saveCustomFood(result.food)
                    Toast.makeText(context, "Saved ${saved.name} to your foods", Toast.LENGTH_SHORT).show()
                    barcodeContinue(nav, Routes.foodAmount(saved.key, meal))
                } else {
                    barcodeContinue(
                        nav,
                        Routes.customFood(
                            "new",
                            barcode = barcode,
                            meal = meal,
                            name = result.food.name.takeIf { it.isNotBlank() },
                            brand = result.food.brand?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
            is OpenFoodFacts.Result.NotFound ->
                barcodeContinue(nav, Routes.customFood("new", barcode = barcode, meal = meal))
            is OpenFoodFacts.Result.Error -> error = result.message
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = { Text("Barcode", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val message = error
            if (message == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "Looking up $barcode on Open Food Facts…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Couldn't reach Open Food Facts (offline?)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Barcode $barcode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { attempt++ },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Retry") }
                            OutlinedButton(
                                onClick = {
                                    barcodeContinue(nav, Routes.customFood("new", barcode = barcode, meal = meal))
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Enter manually") }
                        }
                    }
                }
            }
        }
    }
}
