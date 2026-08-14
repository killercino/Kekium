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

data class Ingredient(
    val name: String,
    val note: String,
    val group: String
)

data class Dish(
    val id: Long,
    val name: String,
    val ingredients: List<Ingredient>
)

data class ShoppingItem(
    val id: Long,
    val name: String,
    val note: String,
    val group: String,
    val bought: Boolean
)

data class Product(
    val id: Long,
    val name: String,
    val note: String,
    val group: String,
    val habitual: Boolean
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

            val ingredients = p[2]
                .split("@@")
                .filter { it.isNotBlank() }
                .mapNotNull {
                    val x = it.split("|", limit = 3)
                    val name = x.getOrNull(0)?.trim().orEmpty()
                    if (name.isBlank()) {
                        null
                    } else {
                        Ingredient(
                            name = name,
                            note = x.getOrNull(1)?.trim().orEmpty(),
                            group = x.getOrNull(2)?.trim().takeUnless { it.isNullOrBlank() }
                                ?: "Otros"
                        )
                    }
                }

            Dish(id, p[1], ingredients)
        }
    }

    fun saveDishes(items: List<Dish>) {
        val raw = items.joinToString("||") { dish ->
            val ingredients = dish.ingredients.joinToString("@@") {
                "${it.name}|${it.note}|${it.group}"
            }
            "${dish.id}##${dish.name}##$ingredients"
        }
        prefs.edit().putString("dishes", raw).apply()
    }

    fun assignment(date: LocalDate): Long? =
        prefs.getLong("day_$date", -1L).takeIf { it >= 0 }

    fun setAssignment(date: LocalDate, dishId: Long?) {
        prefs.edit().apply {
            if (dishId == null) {
                remove("day_$date")
            } else {
                putLong("day_$date", dishId)
            }
        }.apply()
    }

    // Lista de compra actual. Se guarda independientemente del catálogo.
    fun shopping(): List<ShoppingItem> {
        val raw = prefs.getString("shopping_current", null)
            ?: prefs.getString("shopping", "")
            ?: ""

        if (raw.isBlank()) return emptyList()

        return raw.split("||").mapNotNull {
            val p = it.split("::", limit = 6)

            // Compatible con la versión anterior que tenía 6 campos.
            if (p.size >= 6) {
                ShoppingItem(
                    id = p[0].toLongOrNull() ?: return@mapNotNull null,
                    name = p[1],
                    note = p[2],
                    group = p[3].ifBlank { "Otros" },
                    bought = p[4] == "1"
                )
            } else {
                null
            }
        }
    }

    fun saveShopping(items: List<ShoppingItem>) {
        val raw = items.joinToString("||") {
            "${it.id}::${it.name}::${it.note}::${it.group}::${if (it.bought) "1" else "0"}::0"
        }
        prefs.edit()
            .putString("shopping_current", raw)
            .apply()
    }

    /*
     * Catálogo permanente de productos.
     * Borrar un producto de la compra actual NO lo borra de aquí.
     */
    fun products(): List<Product> {
        val raw = prefs.getString("products", null)

        // Migración automática desde la versión anterior:
        // todos los productos que ya existían en la lista pasan al catálogo.
        if (raw == null) {
            val migrated = shopping()
                .distinctBy { "${it.name.lowercase()}|${it.group.lowercase()}" }
                .mapIndexed { index, item ->
                    Product(
                        id = index.toLong() + 1,
                        name = item.name,
                        note = item.note,
                        group = item.group,
                        habitual = false
                    )
                }

            saveProducts(migrated)
            return migrated
        }

        if (raw.isBlank()) return emptyList()

        return raw.split("||").mapNotNull {
            val p = it.split("##", limit = 5)
            if (p.size != 5) {
                null
            } else {
                Product(
                    id = p[0].toLongOrNull() ?: return@mapNotNull null,
                    name = p[1],
                    note = p[2],
                    group = p[3].ifBlank { "Otros" },
                    habitual = p[4] == "1"
                )
            }
        }
    }

    fun saveProducts(items: List<Product>) {
        val raw = items.joinToString("||") {
            "${it.id}##${it.name}##${it.note}##${it.group}##${if (it.habitual) "1" else "0"}"
        }
        prefs.edit().putString("products", raw).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MenuApp(AppStore(this))
        }
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
            modifier = Modifier.fillMaxSize(),
            // Importante: no forzamos una altura de 52dp.
            // TopAppBar gestiona ahora correctamente la zona de la barra de estado.
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (tab == 0) "Menú semanal" else "Compra",
                            maxLines = 1
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
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
            if (tab == 0) {
                MenuScreen(
                    store = store,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                )
            } else {
                ShoppingScreen(
                    store = store,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun MenuScreen(
    store: AppStore,
    modifier: Modifier = Modifier
) {
    var dishes by remember { mutableStateOf(store.dishes()) }
    var weekStart by remember {
        mutableStateOf(
            LocalDate.now().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            )
        )
    }
    var showAdd by remember { mutableStateOf(false) }
    var showDishes by remember { mutableStateOf(false) }

    val locale = Locale("es", "ES")
    val dayFormatter = DateTimeFormatter.ofPattern("EEE d", locale)
    val titleFormatter = DateTimeFormatter.ofPattern("d MMM", locale)

    Column(
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { weekStart = weekStart.minusWeeks(1) },
                modifier = Modifier.size(38.dp)
            ) {
                Text("‹", style = MaterialTheme.typography.titleLarge)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Semana", style = MaterialTheme.typography.labelSmall)
                Text(
                    "${weekStart.format(titleFormatter)} – ${
                        weekStart.plusDays(6).format(titleFormatter)
                    }",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            IconButton(
                onClick = { weekStart = weekStart.plusWeeks(1) },
                modifier = Modifier.size(38.dp)
            ) {
                Text("›", style = MaterialTheme.typography.titleLarge)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items((0..6).toList()) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val assigned = dishes.find { it.id == store.assignment(date) }

                CompactDayRow(
                    label = date
                        .format(dayFormatter)
                        .replaceFirstChar { it.uppercase() },
                    selected = assigned,
                    dishes = dishes,
                    onSelect = { dish ->
                        store.setAssignment(date, dish?.id)
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = {
                    weekStart = LocalDate.now().with(
                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                    )
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Hoy")
            }

            OutlinedButton(
                onClick = { showDishes = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Platos")
            }

            Button(
                onClick = { showAdd = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("+ Plato")
            }
        }
    }

    if (showAdd) {
        AddDishDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, ingredients ->
                val nextId = (dishes.maxOfOrNull { it.id } ?: 0L) + 1
                dishes = dishes + Dish(nextId, name.trim(), ingredients)
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

@Composable
fun CompactDayRow(
    label: String,
    selected: Dish?,
    dishes: List<Dish>,
    onSelect: (Dish?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(78.dp),
                maxLines = 1
            )

            Box(
                modifier = Modifier.weight(1f)
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 3.dp
                    )
                ) {
                    Text(
                        selected?.name ?: "Sin asignar",
                        maxLines = 1
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin asignar") },
                        onClick = {
                            onSelect(null)
                            expanded = false
                        }
                    )

                    dishes.forEach { dish ->
                        DropdownMenuItem(
                            text = { Text(dish.name) },
                            onClick = {
                                onSelect(dish)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDishDialog(
    onDismiss: () -> Unit,
    onAdd: (String, List<Ingredient>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ingredientLines by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo plato") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plato") },
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = ingredientLines,
                    onValueChange = { ingredientLines = it },
                    label = { Text("Ingredientes (opcionales)") },
                    placeholder = {
                        Text(
                            "Ingrediente | nota | grupo\n" +
                                    "Patatas | 1 kg | Fruta y verdura"
                        )
                    },
                    minLines = 5
                )

                Text(
                    "Cada línea: producto | nota | grupo. " +
                            "La nota y el grupo son opcionales.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    val ingredients = ingredientLines.lines().mapNotNull { line ->
                        val x = line.split("|", limit = 3)
                        val n = x.getOrNull(0)?.trim().orEmpty()

                        if (n.isBlank()) {
                            null
                        } else {
                            Ingredient(
                                name = n,
                                note = x.getOrNull(1)?.trim().orEmpty(),
                                group = x.getOrNull(2)?.trim()
                                    .takeUnless { it.isNullOrBlank() }
                                    ?: "Otros"
                            )
                        }
                    }

                    onAdd(name, ingredients)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
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
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                dishes.forEach { dish ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dish.name,
                                modifier = Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = { onDelete(dish) }
                            ) {
                                Text("Borrar")
                            }
                        }

                        if (dish.ingredients.isNotEmpty()) {
                            Text(
                                dish.ingredients.joinToString(", ") {
                                    if (it.note.isNotBlank()) {
                                        "${it.name} (${it.note})"
                                    } else {
                                        it.name
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (dishes.isEmpty()) {
                    Text("No hay platos.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun ShoppingScreen(
    store: AppStore,
    modifier: Modifier = Modifier
) {
    var currentItems by remember { mutableStateOf(store.shopping()) }
    var products by remember { mutableStateOf(store.products()) }
    var section by remember { mutableIntStateOf(0) }

    var showAddCurrent by remember { mutableStateOf(false) }
    var showAddProduct by remember { mutableStateOf(false) }

    val groupOrder = listOf(
        "Fruta y verdura",
        "Carnicería",
        "Pescadería",
        "Huevos y lácteos",
        "Panadería",
        "Despensa",
        "Bebidas",
        "Limpieza",
        "Higiene",
        "Otros"
    )

    Column(
        modifier = modifier.padding(horizontal = 8.dp)
    ) {
        /*
         * Dos apartados independientes:
         * 1. Compra actual
         * 2. Todos los productos
         */
        TabRow(selectedTabIndex = section) {
            Tab(
                selected = section == 0,
                onClick = { section = 0 },
                text = { Text("Compra actual") }
            )
            Tab(
                selected = section == 1,
                onClick = { section = 1 },
                text = { Text("Todos los productos") }
            )
        }

        if (section == 0) {
            CurrentShoppingList(
                items = currentItems,
                groupOrder = groupOrder,
                onAdd = { showAddCurrent = true },
                onToggle = { item ->
                    currentItems = currentItems.map {
                        if (it.id == item.id) {
                            it.copy(bought = !it.bought)
                        } else {
                            it
                        }
                    }
                    store.saveShopping(currentItems)
                },
                onDelete = { item ->
                    // SOLO desaparece de la compra actual.
                    // Sigue existiendo en "Todos los productos".
                    currentItems = currentItems.filterNot { it.id == item.id }
                    store.saveShopping(currentItems)
                },
                onGenerate = {
                    /*
                     * "Generar" añade todos los productos marcados
                     * como habituales. No borra la lista existente.
                     */
                    val nextId = (
                            currentItems.maxOfOrNull { it.id } ?: 0L
                            ) + 1L

                    val existingKeys = currentItems.map {
                        "${it.name.trim().lowercase()}|${it.group.trim().lowercase()}"
                    }.toMutableSet()

                    var id = nextId
                    val additions = products
                        .filter { it.habitual }
                        .filter {
                            val key =
                                "${it.name.trim().lowercase()}|${it.group.trim().lowercase()}"
                            key !in existingKeys
                        }
                        .map {
                            existingKeys.add(
                                "${it.name.trim().lowercase()}|${it.group.trim().lowercase()}"
                            )

                            ShoppingItem(
                                id = id++,
                                name = it.name,
                                note = it.note,
                                group = it.group,
                                bought = false
                            )
                        }

                    currentItems = currentItems + additions
                    store.saveShopping(currentItems)
                }
            )
        } else {
            ProductCatalog(
                products = products,
                groupOrder = groupOrder,
                onAdd = { showAddProduct = true },
                onToggleHabitual = { product ->
                    products = products.map {
                        if (it.id == product.id) {
                            it.copy(habitual = !it.habitual)
                        } else {
                            it
                        }
                    }
                    store.saveProducts(products)
                },
                onAddToCurrent = { product ->
                    val exists = currentItems.any {
                        it.name.equals(product.name, ignoreCase = true) &&
                                it.group.equals(product.group, ignoreCase = true)
                    }

                    if (!exists) {
                        val nextId =
                            (currentItems.maxOfOrNull { it.id } ?: 0L) + 1L

                        currentItems = currentItems + ShoppingItem(
                            id = nextId,
                            name = product.name,
                            note = product.note,
                            group = product.group,
                            bought = false
                        )

                        store.saveShopping(currentItems)
                    }

                    section = 0
                },
                onDelete = { product ->
                    // Se borra del catálogo permanente.
                    // No se toca la compra actual.
                    products = products.filterNot { it.id == product.id }
                    store.saveProducts(products)
                }
            )
        }
    }

    if (showAddCurrent) {
        AddShoppingDialog(
            title = "Añadir a compra actual",
            initialHabitual = false,
            onDismiss = { showAddCurrent = false },
            onAdd = { name, note, group, habitual ->
                // Al añadir manualmente a la compra también se crea/actualiza
                // el producto en el catálogo permanente.
                val existingProduct = products.find {
                    it.name.equals(name.trim(), true) &&
                            it.group.equals(group, true)
                }

                if (existingProduct == null) {
                    val productId =
                        (products.maxOfOrNull { it.id } ?: 0L) + 1L

                    products = products + Product(
                        id = productId,
                        name = name.trim(),
                        note = note.trim(),
                        group = group,
                        habitual = habitual
                    )
                    store.saveProducts(products)
                } else if (habitual && !existingProduct.habitual) {
                    products = products.map {
                        if (it.id == existingProduct.id) {
                            it.copy(
                                note = note.trim().ifBlank { it.note },
                                habitual = true
                            )
                        } else {
                            it
                        }
                    }
                    store.saveProducts(products)
                }

                val nextId =
                    (currentItems.maxOfOrNull { it.id } ?: 0L) + 1L

                currentItems = currentItems + ShoppingItem(
                    id = nextId,
                    name = name.trim(),
                    note = note.trim(),
                    group = group,
                    bought = false
                )

                store.saveShopping(currentItems)
                showAddCurrent = false
            }
        )
    }

    if (showAddProduct) {
        AddShoppingDialog(
            title = "Nuevo producto",
            initialHabitual = true,
            onDismiss = { showAddProduct = false },
            onAdd = { name, note, group, habitual ->
                val duplicate = products.any {
                    it.name.equals(name.trim(), true) &&
                            it.group.equals(group, true)
                }

                if (!duplicate) {
                    val nextId =
                        (products.maxOfOrNull { it.id } ?: 0L) + 1L

                    products = products + Product(
                        id = nextId,
                        name = name.trim(),
                        note = note.trim(),
                        group = group,
                        habitual = habitual
                    )

                    store.saveProducts(products)
                }

                showAddProduct = false
            }
        )
    }
}

@Composable
fun CurrentShoppingList(
    items: List<ShoppingItem>,
    groupOrder: List<String>,
    onAdd: () -> Unit,
    onToggle: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onGenerate: () -> Unit
) {
    val ordered = items.sortedWith(
        compareBy<ShoppingItem> {
            groupOrder.indexOf(it.group).takeIf { i -> i >= 0 }
                ?: groupOrder.size
        }.thenBy {
            it.name.lowercase()
        }
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onGenerate,
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 5.dp
                )
            ) {
                Text("Generar")
            }

            OutlinedButton(
                onClick = onAdd,
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 5.dp
                )
            ) {
                Text("+ Producto")
            }
        }

        if (ordered.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Lista vacía",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Puedes añadir productos manualmente o pulsar Generar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                groupOrder.forEach { group ->
                    val groupItems = ordered.filter { it.group == group }

                    if (groupItems.isNotEmpty()) {
                        item {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(
                                    top = 4.dp,
                                    bottom = 1.dp
                                )
                            )
                        }

                        items(
                            items = groupItems,
                            key = { it.id }
                        ) { item ->
                            ShoppingRowCompact(
                                item = item,
                                onToggle = { onToggle(item) },
                                onDelete = { onDelete(item) }
                            )
                        }
                    }
                }

                val other = ordered.filter {
                    it.group !in groupOrder
                }

                if (other.isNotEmpty()) {
                    item {
                        Text(
                            "Otros",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(
                                top = 4.dp,
                                bottom = 1.dp
                            )
                        )
                    }

                    items(
                        items = other,
                        key = { it.id }
                    ) { item ->
                        ShoppingRowCompact(
                            item = item,
                            onToggle = { onToggle(item) },
                            onDelete = { onDelete(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCatalog(
    products: List<Product>,
    groupOrder: List<String>,
    onAdd: () -> Unit,
    onToggleHabitual: (Product) -> Unit,
    onAddToCurrent: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    val ordered = products.sortedWith(
        compareBy<Product> {
            groupOrder.indexOf(it.group).takeIf { i -> i >= 0 }
                ?: groupOrder.size
        }.thenBy {
            it.name.lowercase()
        }
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onAdd,
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 5.dp
                )
            ) {
                Text("+ Producto")
            }

            Text(
                "${products.size} productos",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        if (ordered.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "No hay productos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Añade aquí todos los productos que quieras conservar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                groupOrder.forEach { group ->
                    val groupItems = ordered.filter { it.group == group }

                    if (groupItems.isNotEmpty()) {
                        item {
                            Text(
                                group,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(
                                    top = 4.dp,
                                    bottom = 1.dp
                                )
                            )
                        }

                        items(
                            items = groupItems,
                            key = { it.id }
                        ) { product ->
                            ProductRowCompact(
                                product = product,
                                onToggleHabitual = {
                                    onToggleHabitual(product)
                                },
                                onAddToCurrent = {
                                    onAddToCurrent(product)
                                },
                                onDelete = {
                                    onDelete(product)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingRowCompact(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = 4.dp,
                vertical = 0.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.bought,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(36.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.bought) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 1
                )

                if (item.note.isNotBlank()) {
                    Text(
                        item.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(
                    horizontal = 6.dp,
                    vertical = 0.dp
                )
            ) {
                Text("×")
            }
        }
    }
}

@Composable
fun ProductRowCompact(
    product: Product,
    onToggleHabitual: () -> Unit,
    onAddToCurrent: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(
                horizontal = 5.dp,
                vertical = 1.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )

                if (product.note.isNotBlank()) {
                    Text(
                        product.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            TextButton(
                onClick = onToggleHabitual,
                contentPadding = PaddingValues(
                    horizontal = 5.dp,
                    vertical = 0.dp
                )
            ) {
                Text(
                    if (product.habitual) "★" else "☆"
                )
            }

            TextButton(
                onClick = onAddToCurrent,
                contentPadding = PaddingValues(
                    horizontal = 5.dp,
                    vertical = 0.dp
                )
            ) {
                Text("+")
            }

            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(
                    horizontal = 5.dp,
                    vertical = 0.dp
                )
            ) {
                Text("×")
            }
        }
    }
}

@Composable
fun AddShoppingDialog(
    title: String,
    initialHabitual: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Otros") }
    var habitual by remember { mutableStateOf(initialHabitual) }
    var expanded by remember { mutableStateOf(false) }

    val groups = listOf(
        "Fruta y verdura",
        "Carnicería",
        "Pescadería",
        "Huevos y lácteos",
        "Panadería",
        "Despensa",
        "Bebidas",
        "Limpieza",
        "Higiene",
        "Otros"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Producto") },
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota (opcional)") },
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                Box {
                    OutlinedButton(
                        onClick = { expanded = true }
                    ) {
                        Text(group)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g) },
                                onClick = {
                                    group = g
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = habitual,
                        onCheckedChange = { habitual = it }
                    )
                    Text("Producto habitual")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onAdd(
                        name.trim(),
                        note.trim(),
                        group,
                        habitual
                    )
                }
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
