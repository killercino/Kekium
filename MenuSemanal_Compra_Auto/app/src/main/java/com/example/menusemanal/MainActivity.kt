package com.example.menusemanal

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Ingredient(val name: String, val quantity: String)
data class Dish(val id: Long, val name: String, val ingredients: List<Ingredient>)
data class ShoppingItem(val id: Long, val name: String, val quantity: String, val bought: Boolean)

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("menu_store", Context.MODE_PRIVATE)

    fun dishes(): List<Dish> {
        val raw = prefs.getString("dishes", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { record ->
            val p = record.split("##", limit = 3)
            if (p.size != 3) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            val ingredients = p[2].split("@@").filter { it.isNotBlank() }.mapNotNull {
                val x = it.split("|", limit = 2)
                if (x.isEmpty()) null else Ingredient(x[0], x.getOrElse(1) { "" })
            }
            Dish(id, p[1], ingredients)
        }
    }

    fun saveDishes(items: List<Dish>) {
        val raw = items.joinToString("||") { dish ->
            val ing = dish.ingredients.joinToString("@@") { "${it.name}|${it.quantity}" }
            "${dish.id}##${dish.name}##$ing"
        }
        prefs.edit().putString("dishes", raw).apply()
    }

    fun assignment(date: LocalDate): Long? =
        prefs.getLong("day_$date", -1L).takeIf { it >= 0 }

    fun setAssignment(date: LocalDate, dishId: Long?) {
        prefs.edit().apply {
            if (dishId == null) remove("day_$date") else putLong("day_$date", dishId)
        }.apply()
    }

    fun shopping(): List<ShoppingItem> {
        val raw = prefs.getString("shopping", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull {
            val p = it.split("::", limit = 4)
            if (p.size == 4) ShoppingItem(
                p[0].toLongOrNull() ?: return@mapNotNull null,
                p[1], p[2], p[3] == "1"
            ) else null
        }
    }

    fun saveShopping(items: List<ShoppingItem>) {
        prefs.edit().putString(
            "shopping",
            items.joinToString("||") {
                "${it.id}::${it.name}::${it.quantity}::${if (it.bought) "1" else "0"}"
            }
        ).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppStore(this)
        setContent { MenuApp(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuApp(store: AppStore) {
    var tab by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF4F6354),
            secondary = Color(0xFF6F8B78)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(if (tab == 0) "Menú semanal" else "Lista de la compra")
                })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Text("🍽") }, label = { Text("Menú") }
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Text("🛒") }, label = { Text("Compra") }
                    )
                }
            }
        ) { padding ->
            if (tab == 0) MenuScreen(store, Modifier.padding(padding))
            else ShoppingScreen(store, Modifier.padding(padding))
        }
    }
}

@Composable
fun MenuScreen(store: AppStore, modifier: Modifier = Modifier) {
    var dishes by remember { mutableStateOf(store.dishes()) }
    var weekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var showAdd by remember { mutableStateOf(false) }
    var showDishes by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val locale = Locale("es", "ES")
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE d", locale)
    val titleFormatter = DateTimeFormatter.ofPattern("d MMMM", locale)

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Semana", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${weekStart.format(titleFormatter)} – ${weekStart.plusDays(6).format(titleFormatter)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›") }
            }

            Button(
                onClick = {
                    weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                },
                Modifier.fillMaxWidth()
            ) { Text("Ir a esta semana") }

            Spacer(Modifier.height(10.dp))

            if (dishes.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Añade tus platos", style = MaterialTheme.typography.titleMedium)
                        Text("Cada plato puede tener ingredientes para generar la compra automáticamente.")
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items((0..6).toList()) { offset ->
                        val date = weekStart.plusDays(offset.toLong())
                        val assigned = dishes.find { it.id == store.assignment(date) }
                        DayCard(
                            label = date.format(dayFormatter).replaceFirstChar { it.uppercase() },
                            selected = assigned,
                            dishes = dishes,
                            onSelect = { store.setAssignment(date, it?.id) }
                        )
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(onClick = { showDishes = true }) { Text("☰") }
            FloatingActionButton(onClick = { showAdd = true }) { Text("+") }
        }

        message?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(1800)
                message = null
            }
            Snackbar(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                action = {}
            ) { Text(it) }
        }
    }

    if (showAdd) {
        AddDishDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, ingredients ->
                val nextId = (dishes.maxOfOrNull { it.id } ?: 0L) + 1
                val updated = dishes + Dish(nextId, name.trim(), ingredients)
                dishes = updated
                store.saveDishes(updated)
                showAdd = false
            }
        )
    }

    if (showDishes) {
        ManageDishesDialog(
            dishes = dishes,
            onDismiss = { showDishes = false },
            onDelete = {
                val updated = dishes.filterNot { d -> d.id == it.id }
                dishes = updated
                store.saveDishes(updated)
            }
        )
    }
}

@Composable
fun DayCard(
    label: String,
    selected: Dish?,
    dishes: List<Dish>,
    onSelect: (Dish?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    Modifier.fillMaxWidth()
                ) { Text(selected?.name ?: "Sin asignar") }

                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Sin asignar") },
                        onClick = { onSelect(null); expanded = false }
                    )
                    dishes.forEach { dish ->
                        DropdownMenuItem(
                            text = { Text(dish.name) },
                            onClick = { onSelect(dish); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDishDialog(onDismiss: () -> Unit, onAdd: (String, List<Ingredient>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ingredientsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo plato") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nombre del plato") },
                    placeholder = { Text("Ej. Tortilla de patatas") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text("Ingredientes") },
                    placeholder = { Text("Un ingrediente por línea:\npatatas | 1 kg\nhuevos | 6\naceite | 100 ml") },
                    minLines = 5
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escribe: ingrediente | cantidad",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    val ingredients = ingredientsText.lines().mapNotNull { line ->
                        val x = line.split("|", limit = 2)
                        val n = x.getOrNull(0)?.trim().orEmpty()
                        if (n.isBlank()) null else Ingredient(n, x.getOrNull(1)?.trim().orEmpty())
                    }
                    onAdd(name, ingredients)
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ManageDishesDialog(
    dishes: List<Dish>,
    onDismiss: () -> Unit,
    onDelete: (Dish) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mis platos") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (dishes.isEmpty()) Text("No hay platos.")
                dishes.forEach { dish ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dish.name, Modifier.weight(1f))
                            TextButton(onClick = { onDelete(dish) }) { Text("Borrar") }
                        }
                        if (dish.ingredients.isNotEmpty()) {
                            Text(
                                dish.ingredients.joinToString(", ") {
                                    if (it.quantity.isBlank()) it.name else "${it.name} (${it.quantity})"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun ShoppingScreen(store: AppStore, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf(store.shopping()) }
    var dishes by remember { mutableStateOf(store.dishes()) }
    var weekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var showAdd by remember { mutableStateOf(false) }

    fun generateFromWeek() {
        dishes = store.dishes()
        val totals = linkedMapOf<String, MutableList<String>>()
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            val dish = dishes.find { it.id == store.assignment(date) } ?: continue
            for (ing in dish.ingredients) {
                val key = ing.name.trim().lowercase()
                if (key.isBlank()) continue
                totals.getOrPut(key) { mutableListOf() }
                    .add(ing.quantity.trim())
            }
        }

        val generated = totals.entries.mapIndexed { index, (name, quantities) ->
            ShoppingItem(
                id = 1_000_000L + index,
                name = name.replaceFirstChar { it.uppercase() },
                quantity = quantities.filter { it.isNotBlank() }.joinToString(" + "),
                bought = false
            )
        }

        val manual = items.filter { it.id < 1_000_000L }
        items = manual + generated
        store.saveShopping(items)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Compra de la semana", style = MaterialTheme.typography.labelMedium)
                Text(weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))))
            }
            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›") }
        }

        Button(onClick = { generateFromWeek() }, Modifier.fillMaxWidth()) {
            Text("Generar compra desde el menú")
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Lista vacía", style = MaterialTheme.typography.titleMedium)
                    Text("Puedes añadir productos manualmente o generarlos desde el menú.")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ShoppingRow(
                        item,
                        onToggle = {
                            items = items.map { if (it.id == item.id) it.copy(bought = !it.bought) else it }
                            store.saveShopping(items)
                        },
                        onDelete = {
                            items = items.filterNot { it.id == item.id }
                            store.saveShopping(items)
                        }
                    )
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showAdd = true },
            Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Text("+") }
    }

    if (showAdd) {
        AddShoppingDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, quantity ->
                val nextId = (items.filter { it.id < 1_000_000L }.maxOfOrNull { it.id } ?: 0L) + 1
                items = items + ShoppingItem(nextId, name.trim(), quantity.trim(), false)
                store.saveShopping(items)
                showAdd = false
            }
        )
    }
}

@Composable
fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(item.bought, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None
                )
                if (item.quantity.isNotBlank()) Text(item.quantity, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Borrar") }
        }
    }
}

@Composable
fun AddShoppingDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir producto") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Producto") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Cantidad") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.trim().isNotEmpty(), onClick = { onAdd(name, quantity) }) {
                Text("Añadir")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
