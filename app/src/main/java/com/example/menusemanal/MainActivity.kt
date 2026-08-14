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

data class Ingredient(val name: String, val note: String, val group: String)
data class Dish(val id: Long, val name: String, val ingredients: List<Ingredient>)
data class Product(val id: Long, val name: String, val note: String, val group: String)
data class ShoppingItem(val id: Long, val product: Product, val bought: Boolean)

private val groups = listOf(
    "Fruta y verdura", "Carnicería", "Pescadería", "Huevos y lácteos",
    "Panadería", "Despensa", "Bebidas", "Limpieza", "Higiene", "Otros"
)

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
                val x = it.split("|", limit = 3)
                val name = x.getOrNull(0)?.trim().orEmpty()
                if (name.isBlank()) null else Ingredient(
                    name = name,
                    note = x.getOrNull(1)?.trim().orEmpty(),
                    group = x.getOrNull(2)?.trim().orEmpty().ifBlank { "Otros" }
                )
            }
            Dish(id, p[1], ingredients)
        }
    }

    fun saveDishes(items: List<Dish>) {
        val raw = items.joinToString("||") { dish ->
            val ing = dish.ingredients.joinToString("@@") { "${it.name}|${it.note}|${it.group}" }
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

    fun habituals(): List<Product> {
        val raw = prefs.getString("habituals", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { record ->
            val p = record.split("::", limit = 4)
            if (p.size != 4) return@mapNotNull null
            val id = p[0].toLongOrNull() ?: return@mapNotNull null
            Product(id, p[1], p[2], p[3])
        }
    }

    fun saveHabituals(items: List<Product>) {
        val raw = items.joinToString("||") { "${it.id}::${it.name}::${it.note}::${it.group}" }
        prefs.edit().putString("habituals", raw).apply()
    }

    fun shopping(): List<ShoppingItem> {
        val raw = prefs.getString("shopping", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { record ->
            val p = record.split("::", limit = 5)
            if (p.size == 5) {
                val id = p[0].toLongOrNull() ?: return@mapNotNull null
                ShoppingItem(id, Product(id, p[1], p[2], p[3]), p[4] == "1")
            } else {
                // Migration from v1.1/v1.2: old format was id::name::quantity::bought.
                val old = record.split("::", limit = 4)
                if (old.size == 4) {
                    val id = old[0].toLongOrNull() ?: return@mapNotNull null
                    ShoppingItem(id, Product(id, old[1], old[2], "Otros"), old[3] == "1")
                } else null
            }
        }
    }

    fun saveShopping(items: List<ShoppingItem>) {
        val raw = items.joinToString("||") {
            "${it.id}::${it.product.name}::${it.product.note}::${it.product.group}::${if (it.bought) "1" else "0"}"
        }
        prefs.edit().putString("shopping", raw).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MenuApp(AppStore(this)) }
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
                TopAppBar(
                    title = { Text(if (tab == 0) "Menú semanal" else "Lista de la compra") },
                    modifier = Modifier.height(52.dp)
                )
            },
            bottomBar = {
                NavigationBar(modifier = Modifier.height(68.dp)) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("🍽") },
                        label = { Text("Menú") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("🛒") },
                        label = { Text("Compra") }
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
    var weekStart by remember { mutableStateOf(currentWeek()) }
    var showAdd by remember { mutableStateOf(false) }
    var showDishes by remember { mutableStateOf(false) }

    val locale = Locale("es", "ES")
    val dayFormatter = DateTimeFormatter.ofPattern("EEE d", locale)
    val weekFormatter = DateTimeFormatter.ofPattern("d MMM", locale)

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) { Text("‹") }
            Text(
                "${weekStart.format(weekFormatter)} – ${weekStart.plusDays(6).format(weekFormatter)}",
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) { Text("›") }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items((0..6).toList()) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val selected = dishes.find { it.id == store.assignment(date) }
                DayRowCompact(
                    label = date.format(dayFormatter).replaceFirstChar { it.uppercase() },
                    selected = selected,
                    dishes = dishes,
                    onSelect = { store.setAssignment(date, it?.id) }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = { weekStart = currentWeek() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("Esta semana") }
            OutlinedButton(
                onClick = { showDishes = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("Platos") }
            Button(
                onClick = { showAdd = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) { Text("+ Plato") }
        }
    }

    if (showAdd) {
        AddDishDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, ingredients ->
                val id = (dishes.maxOfOrNull { it.id } ?: 0L) + 1
                dishes = dishes + Dish(id, name.trim(), ingredients)
                store.saveDishes(dishes)
                showAdd = false
            }
        )
    }

    if (showDishes) {
        ManageDishesDialog(
            dishes = dishes,
            onDismiss = { showDishes = false },
            onDelete = { dish ->
                dishes = dishes.filterNot { it.id == dish.id }
                store.saveDishes(dishes)
            }
        )
    }
}

fun currentWeek(): LocalDate =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

@Composable
fun DayRowCompact(
    label: String,
    selected: Dish?,
    dishes: List<Dish>,
    onSelect: (Dish?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    Modifier.fillMaxWidth().height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(selected?.name ?: "Sin asignar", maxLines = 1)
                }
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
    var lines by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo plato") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Plato") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = lines,
                    onValueChange = { lines = it },
                    label = { Text("Ingredientes opcionales") },
                    placeholder = { Text("Patatas | 1 kg | Fruta y verdura\nHuevos | 6 | Huevos y lácteos") },
                    minLines = 4
                )
                Spacer(Modifier.height(4.dp))
                Text("Formato: producto | nota | grupo", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.trim().isNotEmpty(), onClick = {
                val ingredients = lines.lines().mapNotNull { line ->
                    val p = line.split("|", limit = 3)
                    val product = p.getOrNull(0)?.trim().orEmpty()
                    if (product.isBlank()) null else Ingredient(
                        product,
                        p.getOrNull(1)?.trim().orEmpty(),
                        p.getOrNull(2)?.trim().orEmpty().ifBlank { "Otros" }
                    )
                }
                onAdd(name, ingredients)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ManageDishesDialog(dishes: List<Dish>, onDismiss: () -> Unit, onDelete: (Dish) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mis platos") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                dishes.forEach { dish ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(dish.name, Modifier.weight(1f))
                        TextButton(onClick = { onDelete(dish) }) { Text("Borrar") }
                    }
                }
                if (dishes.isEmpty()) Text("No hay platos.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
fun ShoppingScreen(store: AppStore, modifier: Modifier = Modifier) {
    var items by remember { mutableStateOf(store.shopping()) }
    var habituals by remember { mutableStateOf(store.habituals()) }
    var showAdd by remember { mutableStateOf(false) }
    var showHabituals by remember { mutableStateOf(false) }

    val ordered = items.sortedWith(
        compareBy<ShoppingItem> { groupRank(it.product.group) }
            .thenBy { it.product.name.lowercase() }
    )

    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { showAdd = true },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
            ) { Text("+ Producto") }
            OutlinedButton(
                onClick = { showHabituals = true },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
            ) { Text("Habituales") }
            OutlinedButton(
                onClick = {
                    val existing = items.map { "${it.product.name.lowercase()}|${it.product.group.lowercase()}" }.toSet()
                    val additions = habituals.filter { "${it.name.lowercase()}|${it.group.lowercase()}" !in existing }
                        .mapIndexed { index, product ->
                            ShoppingItem((items.maxOfOrNull { it.id } ?: 0L) + index + 1, product, false)
                        }
                    items = items + additions
                    store.saveShopping(items)
                },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
            ) { Text("Generar lista") }
        }

        if (ordered.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Lista vacía", style = MaterialTheme.typography.titleMedium)
                    Text("Añade productos o crea tus habituales.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                groups.forEach { group ->
                    val groupItems = ordered.filter { it.product.group == group }
                    if (groupItems.isNotEmpty()) {
                        item {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
                            )
                        }
                        items(groupItems, key = { it.id }) { item ->
                            ShoppingRowCompact(
                                item = item,
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
                val unknown = ordered.filter { it.product.group !in groups }
                if (unknown.isNotEmpty()) {
                    item { Text("Otros", style = MaterialTheme.typography.labelMedium) }
                    items(unknown, key = { it.id }) { item ->
                        ShoppingRowCompact(
                            item = item,
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
    }

    if (showAdd) {
        AddProductDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, note, group, habitual ->
                val productId = (habituals.maxOfOrNull { it.id } ?: 0L) + 1
                if (habitual) {
                    val product = Product(productId, name.trim(), note.trim(), group)
                    habituals = habituals + product
                    store.saveHabituals(habituals)
                }
                val shoppingId = (items.maxOfOrNull { it.id } ?: 0L) + 1
                items = items + ShoppingItem(shoppingId, Product(shoppingId, name.trim(), note.trim(), group), false)
                store.saveShopping(items)
                showAdd = false
            }
        )
    }

    if (showHabituals) {
        HabitualsDialog(
            items = habituals,
            onDismiss = { showHabituals = false },
            onDelete = { product ->
                habituals = habituals.filterNot { it.id == product.id }
                store.saveHabituals(habituals)
            }
        )
    }
}

fun groupRank(group: String): Int = groups.indexOf(group).let { if (it < 0) groups.lastIndex else it }

@Composable
fun ShoppingRowCompact(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.bought,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(34.dp)
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None
                )
                if (item.product.note.isNotBlank()) {
                    Text(item.product.note, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
            ) { Text("×") }
        }
    }
}

@Composable
fun AddProductDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Otros") }
    var habitual by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir producto") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Producto") }, singleLine = true)
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Nota (opcional)") }, singleLine = true)
                Spacer(Modifier.height(5.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(group) }
                    DropdownMenu(expanded, { expanded = false }) {
                        groups.forEach { g ->
                            DropdownMenuItem(text = { Text(g) }, onClick = { group = g; expanded = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = habitual, onCheckedChange = { habitual = it })
                    Text("Guardar como habitual")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.trim().isNotEmpty(), onClick = { onAdd(name, note, group, habitual) }) {
                Text("Añadir")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun HabitualsDialog(items: List<Product>, onDismiss: () -> Unit, onDelete: (Product) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Productos habituales") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (items.isEmpty()) Text("No tienes productos habituales.")
                items.forEach { product ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name)
                            Text(product.group, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onDelete(product) }) { Text("Borrar") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
