package com.mipresupuesto.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.io.ByteArrayOutputStream
import java.io.InputStream

private val DeepGreen = Color(0xFF103F35)
private val Green = Color(0xFF167C62)
private val SoftGreen = Color(0xFFE7F5EC)
private val AppBackground = Color(0xFFF7FAF8)
private val Muted = Color(0xFF65766F)
private val ExpenseRed = Color(0xFFCE5B54)
private val WarningOrange = Color(0xFFC78326)
private val White = Color.White

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 250, 248)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green,
                    onPrimary = White,
                    background = AppBackground,
                    surface = White,
                    onSurface = DeepGreen,
                    outline = Color(0xFFDDE9E2)
                )
            ) {
                BudgetApp()
            }
        }
    }
}

@Composable
private fun BudgetApp(vm: FinanceViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Movement?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = runCatching {
                    val csv = vm.exportCsv()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(csv.toByteArray(Charsets.UTF_8))
                            output.flush()
                        } ?: error("No fue posible abrir el archivo")
                    }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (success) "Archivo CSV guardado" else "No se pudo exportar",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            val success = runCatching {
                val backup = vm.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo crear el archivo")
                }
            }.isSuccess
            Toast.makeText(context,
                if (success) "Copia de seguridad guardada" else "No se pudo guardar la copia",
                Toast.LENGTH_LONG).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImportUri = uri }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Restaurar copia de seguridad") },
            text = { Text("Esta acción reemplazará TODOS los movimientos y presupuestos guardados actualmente en este celular. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    scope.launch {
                        val outcome = runCatching {
                            val backupText = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    readBackupText(stream)
                                } ?: error("No se pudo leer el archivo")
                            }
                            vm.importBackup(backupText)
                        }
                        Toast.makeText(context,
                            if (outcome.isSuccess) "Copia restaurada correctamente"
                            else "No se restauró: ${outcome.exceptionOrNull()?.message ?: "archivo no válido"}",
                            Toast.LENGTH_LONG).show()
                    }
                }) { Text("Reemplazar datos", color = ExpenseRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(AppBackground)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                        .background(DeepGreen),
                    contentAlignment = Alignment.Center
                ) { Text("$", color = White, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MI PRESUPUESTO", fontSize = 10.sp, color = Muted, letterSpacing = 2.sp)
                    Text("Tu dinero, en orden", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DeepGreen)
                }
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Exportar o restaurar datos", tint = Green)
                    }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar movimientos (CSV)") },
                            onClick = {
                                actionsExpanded = false
                                exportLauncher.launch("mis_movimientos_${state.month}.csv")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Guardar copia de seguridad") },
                            onClick = {
                                actionsExpanded = false
                                backupLauncher.launch("mi_presupuesto_respaldo.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restaurar copia de seguridad") },
                            onClick = {
                                actionsExpanded = false
                                restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                listOf(
                    Triple("Inicio", Icons.Default.Home, 0),
                    Triple("Movimientos", Icons.Default.ListAlt, 1),
                    Triple("Presupuestos", Icons.Default.PieChart, 2)
                ).forEach { (title, icon, destination) ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DeepGreen,
                            selectedTextColor = DeepGreen,
                            indicatorColor = SoftGreen
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { editing = null; showEditor = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo movimiento") },
                    containerColor = DeepGreen,
                    contentColor = White
                )
            }
        }
    ) { scaffoldPadding ->
        Column(
            Modifier.fillMaxSize().padding(scaffoldPadding)
        ) {
            MonthSelector(
                month = state.month,
                onPrevious = { vm.changeMonth(-1) },
                onNext = { vm.changeMonth(1) }
            )
            when (tab) {
                0 -> OverviewScreen(state,
                    onAdd = { editing = null; showEditor = true },
                    onNavigateBudgets = { tab = 2 })
                1 -> HistoryScreen(
                    state = state,
                    onEdit = { editing = it; showEditor = true },
                    onDelete = { vm.deleteMovement(it) }
                )
                else -> BudgetsScreen(state, onSave = { category, amount ->
                    vm.saveLimit(state.month, category, amount)
                })
            }
        }
    }
    if (showEditor) {
        MovementEditor(
            movement = editing,
            initialDay = firstDayForSelectedMonth(state.month),
            onDismiss = { showEditor = false },
            onSave = { move ->
                vm.saveMovement(move)
                showEditor = false
            }
        )
    }
}

@Composable
private fun MonthSelector(month: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ArrowBackIosNew, "Mes anterior", tint = Green, modifier = Modifier.size(18.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PERÍODO", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Text(displayMonth(month), color = DeepGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ArrowForwardIos, "Mes siguiente", tint = Green, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun OverviewScreen(state: FinanceState, onAdd: () -> Unit, onNavigateBudgets: () -> Unit) {
    val stats = state.stats
    val expensesByCategory = expenseCategories.map { category ->
        category to state.spentOn(category.key)
    }.filter { it.second > 0 }.sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(DeepGreen, Green)))
                    .padding(22.dp)
            ) {
                Text("BALANCE DEL MES", color = Color(0xFFC9E9D9), fontSize = 11.sp, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(7.dp))
                Text(cop(stats.balance), color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Ingresos menos gastos de este mes", color = Color(0xFFD3EDE1), fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("↙ Ingresos", fontSize = 12.sp, color = Color(0xFFC5EAD5))
                        Text(cop(stats.income), fontWeight = FontWeight.SemiBold, color = White, fontSize = 19.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("↗ Gastos", fontSize = 12.sp, color = Color(0xFFC5EAD5))
                        Text(cop(stats.expense), fontWeight = FontWeight.SemiBold, color = White, fontSize = 19.sp)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Presupuesto mensual", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onNavigateBudgets) { Text("Configurar") }
                    }
                    val limit = stats.budget
                    if (limit == null) {
                        Text("Define cuánto quieres gastar este mes.", color = Muted, fontSize = 13.sp)
                    } else {
                        Text("${cop(stats.expense)} de ${cop(limit)}", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { stats.budgetUsage },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                            color = when {
                                stats.expense > limit -> ExpenseRed
                                stats.budgetUsage >= 0.8f -> WarningOrange
                                else -> Green
                            },
                            trackColor = SoftGreen
                        )
                        Spacer(Modifier.height(12.dp))
                        val left = stats.remainingBudget ?: 0
                        Text(
                            when {
                                left < 0 -> "Has superado el límite por ${cop(-left)}"
                                stats.budgetUsage >= 0.8f -> "¡Atención! Te quedan ${cop(left)} de presupuesto"
                                else -> "Te quedan ${cop(left)} de presupuesto"
                            },
                            fontWeight = FontWeight.Medium,
                            color = when {
                                left < 0 -> ExpenseRed
                                stats.budgetUsage >= 0.8f -> WarningOrange
                                else -> Green
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        item { SectionHeading("¿En qué gastaste?") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = White), shape = RoundedCornerShape(19.dp)) {
                Column(Modifier.padding(18.dp)) {
                    if (expensesByCategory.isEmpty()) {
                        EmptyHint("Aún no tienes gastos registrados este mes.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpensesDonut(expensesByCategory, Modifier.size(144.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                expensesByCategory.take(5).forEach { (category, total) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(category.color)))
                                        Spacer(Modifier.width(7.dp))
                                        Column {
                                            Text(category.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(cop(total), color = Muted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeading("Últimos movimientos") }
        if (state.movements.isEmpty()) {
            item { EmptyHint("Registra tu primer movimiento para comenzar.") }
        } else {
            items(state.movements.take(5), key = { it.id }) { movement ->
                MovementRow(movement, onEdit = {}, onDelete = {}, editable = false)
            }
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Registrar ingreso o gasto") }
        }
    }
}

@Composable
private fun ExpensesDonut(data: List<Pair<Category, Long>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }.toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            var start = -90f
            data.forEach { (cat, amount) ->
                val sweep = if (total > 0) amount / total * 360f else 0f
                drawArc(
                    color = Color(cat.color), startAngle = start,
                    sweepAngle = (sweep - 2f).coerceAtLeast(0f), useCenter = false,
                    style = Stroke(width = 25.dp.toPx(), cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("GASTOS", color = Muted, fontSize = 10.sp)
            Text("${data.size}", color = DeepGreen, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("categorías", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun HistoryScreen(
    state: FinanceState,
    onEdit: (Movement) -> Unit,
    onDelete: (Movement) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("TODOS") }
    var deleteTarget by remember { mutableStateOf<Movement?>(null) }
    val visible = state.movements.filter { movement ->
        (filter == "TODOS" || movement.kind == filter) &&
            (query.isBlank() || categoryFor(movement).label.contains(query, ignoreCase = true) ||
                movement.note.contains(query, ignoreCase = true) ||
                movement.amount.toString().contains(query))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar movimientos") }, singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TODOS" to "Todos", "GASTO" to "Gastos", "INGRESO" to "Ingresos")
                .forEach { (value, name) ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(name) })
                }
        }
        Spacer(Modifier.height(8.dp))
        Text("${visible.size} movimientos", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        if (visible.isEmpty()) {
            EmptyHint("No hay movimientos que coincidan con tu búsqueda.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 105.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(visible, key = { it.id }) { movement ->
                    MovementRow(movement, onEdit = { onEdit(movement) },
                        onDelete = { deleteTarget = movement })
                }
            }
        }
    }
    deleteTarget?.let { movement ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar movimiento") },
            text = { Text("¿Quieres eliminar ${cop(movement.amount)} de ${categoryFor(movement).label}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { onDelete(movement); deleteTarget = null }) {
                    Text("Eliminar", color = ExpenseRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun MovementRow(
    movement: Movement,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editable: Boolean = true
) {
    val category = categoryFor(movement)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(White).padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Color(category.color).copy(alpha = .13f)),
            contentAlignment = Alignment.Center
        ) { Text(category.emoji, fontSize = 21.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(displayDay(movement.day), movement.note).filter { it.isNotBlank() }.joinToString(" · "),
                color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (movement.kind == "INGRESO") "+" else "−") + cop(movement.amount),
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = if (movement.kind == "INGRESO") Green else DeepGreen
            )
            if (editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Editar", tint = Muted, modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Eliminar", tint = ExpenseRed, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsScreen(state: FinanceState, onSave: (String, Long?) -> Unit) {
    var editCategory by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Organiza tus límites", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text("Establece cuánto deseas gastar. Los límites no modifican tu saldo.", color = Muted, fontSize = 13.sp)
        }
        item {
            BudgetEntry(
                title = "Límite mensual total", emoji = "🎯",
                spent = state.stats.expense,
                limit = state.limitFor("TOTAL"),
                onClick = { editCategory = "TOTAL" }
            )
        }
        item { SectionHeading("Por categoría") }
        items(expenseCategories, key = { it.key }) { cat ->
            BudgetEntry(
                title = cat.label, emoji = cat.emoji,
                spent = state.spentOn(cat.key),
                limit = state.limitFor(cat.key),
                onClick = { editCategory = cat.key }
            )
        }
        item {
            Text(
                "Los presupuestos se configuran por mes. Los gastos se cuentan automáticamente según su fecha.",
                color = Muted, fontSize = 12.sp
            )
        }
    }
    editCategory?.let { category ->
        BudgetEditor(
            month = state.month,
            category = category,
            current = state.limitFor(category),
            onDismiss = { editCategory = null },
            onSave = { amount -> onSave(category, amount); editCategory = null }
        )
    }
}

@Composable
private fun BudgetEntry(title: String, emoji: String, spent: Long, limit: Long?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(White)
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 23.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Gastado: ${cop(spent)}", color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (limit == null) "Definir" else cop(limit), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Editar ›", color = Muted, fontSize = 11.sp)
            }
        }
        if (limit != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (spent.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                color = when {
                    spent > limit -> ExpenseRed
                    spent.toDouble() / limit.toDouble() >= 0.8 -> WarningOrange
                    else -> Green
                },
                trackColor = SoftGreen
            )
            if (spent > limit) {
                Spacer(Modifier.height(6.dp))
                Text("Excedido por ${cop(spent - limit)}", color = ExpenseRed, fontSize = 12.sp)
            } else if (spent.toDouble() / limit.toDouble() >= 0.8) {
                Spacer(Modifier.height(6.dp))
                Text("Atención: estás cerca del límite", color = WarningOrange, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BudgetEditor(
    month: String,
    category: String,
    current: Long?,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit
) {
    var amount by remember(category, month) { mutableStateOf(current?.toString() ?: "") }
    var error by remember(category, month) { mutableStateOf(false) }
    val label = if (category == "TOTAL") "Presupuesto mensual" else expenseCategories.find { it.key == category }?.label ?: category
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                Text("Límite para ${displayMonth(month)} en pesos colombianos.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; error = false },
                    label = { Text("Valor en COP") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error, singleLine = true
                )
                if (error) Text("Escribe un valor válido mayor que cero o deja vacío para quitar el límite.", color = ExpenseRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseCop(amount)
                if (amount.isBlank() || parsed != null) onSave(parsed) else error = true
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MovementEditor(movement: Movement?, initialDay: Long, onDismiss: () -> Unit, onSave: (Movement) -> Unit) {
    val context = LocalContext.current
    var kind by remember(movement?.id) { mutableStateOf(movement?.kind ?: "GASTO") }
    var amountText by remember(movement?.id) { mutableStateOf(movement?.amount?.toString() ?: "") }
    var category by remember(movement?.id) { mutableStateOf(movement?.category ?: expenseCategories.first().key) }
    var note by remember(movement?.id) { mutableStateOf(movement?.note ?: "") }
    var day by remember(movement?.id) { mutableLongStateOf(movement?.day ?: initialDay) }
    var categoryMenu by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val categories = if (kind == "GASTO") expenseCategories else incomeCategories

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(max = 670.dp),
            color = White,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                Text(if (movement == null) "Nuevo movimiento" else "Editar movimiento",
                    fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("Tu información se guarda en este dispositivo.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FilterChip(
                        selected = kind == "GASTO", onClick = { kind = "GASTO"; category = expenseCategories.first().key },
                        label = { Text("↗ Gasto") }
                    )
                    FilterChip(
                        selected = kind == "INGRESO", onClick = { kind = "INGRESO"; category = incomeCategories.first().key },
                        label = { Text("↙ Ingreso") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; invalid = false },
                    label = { Text("Monto en pesos colombianos") },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    isError = invalid
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${categories.find { it.key == category }?.emoji ?: "📦"}  Categoría: ${categories.find { it.key == category }?.label ?: "Otros"}  ▾")
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.emoji}  ${option.label}") },
                                onClick = { category = option.key; categoryMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = {
                    val date = LocalDate.ofEpochDay(day)
                    DatePickerDialog(context, { _, year, month, dateOfMonth ->
                        day = LocalDate.of(year, month + 1, dateOfMonth).toEpochDay()
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("📅  Fecha: ${displayDay(day)}")
                }
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(180) },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                if (invalid) {
                    Spacer(Modifier.height(6.dp))
                    Text("Escribe un valor válido mayor que cero.", color = ExpenseRed, fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(onClick = {
                        val parsed = parseCop(amountText)
                        if (parsed == null) invalid = true
                        else onSave(
                            Movement(
                                id = movement?.id ?: 0,
                                kind = kind,
                                amount = parsed,
                                category = category,
                                day = day,
                                note = note.trim(),
                                createdAt = movement?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    }, modifier = Modifier.weight(1f)) { Text("Guardar") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(value: String) {
    Text(value, fontWeight = FontWeight.Bold, color = DeepGreen, fontSize = 18.sp)
}

@Composable
private fun EmptyHint(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(White).padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, textAlign = TextAlign.Center, fontSize = 13.sp, color = Muted)
    }
}


/** Evita cargar respaldos desproporcionadamente grandes en memoria. */
private fun readBackupText(input: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        total += count
        require(total <= 15_000_000) { "El archivo es demasiado grande." }
        output.write(buffer, 0, count)
    }
    return output.toString("UTF-8")
}
