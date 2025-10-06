package com.apollomonasa.scheduleapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apollomonasa.scheduleapp.ui.theme.ScheduleAppTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.*


data class GradeRule(
    val grade: String = "",
    val shiftsPerWeek: String = "1",
    val needsSeniorBuddy: Boolean = false,
    val canDoNightShift: Boolean = true
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScheduleAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}


data class RawScheduleEntry(
    var name: String = "",
    var studentId: String = "",
    var classTimeString: String = ""
)

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object DataImport : NavItem("import", "导入数据", Icons.Default.UploadFile)
    object Schedule : NavItem("schedule", "排班表", Icons.Default.DateRange)
    object Settings : NavItem("settings", "设置", Icons.Default.Settings)
}

@SuppressLint("ContextCastToActivity")
@Composable
fun MainScreen(scheduleViewModel: ScheduleViewModel = viewModel()) {
    val navController = rememberNavController()
    val navItems = listOf(NavItem.DataImport, NavItem.Schedule, NavItem.Settings)
    val context = LocalContext.current
    val personDao = AppDatabase.getDatabase(context).personDao()
    val scope = rememberCoroutineScope()

    val activity = (LocalContext.current as? MainActivity)
    LaunchedEffect(key1 = activity?.intent?.data) {
        val intent = activity?.intent
        if (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT) {
            intent.data?.let { uri ->
                Log.d("ExternalFile", "接收到外部文件URI: $uri")
                scope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use {
                            val peopleFromFile = processSingleExcelFile(it)
                            personDao.upsertAll(peopleFromFile)
                            withContext(Dispatchers.Main) {
                                Log.d("ExternalFile", "外部文件导入成功！")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ExternalFile", "处理外部文件失败", e)
                    }
                }
                activity.intent.data = null // 处理完后清空，防止旋转屏幕等操作重复处理
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                navItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.DataImport.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavItem.DataImport.route) {
                DataImportScreen(
                    personDao = personDao,
                    onNavigateToDetail = { studentId ->
                        navController.navigate("personDetail/$studentId")
                    }
                )
            }

            composable(
                route = "personDetail/{studentId}",
                arguments = listOf(navArgument("studentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val studentId = backStackEntry.arguments?.getString("studentId")
                if (studentId != null) {
                    PersonDetailScreen(
                        studentId = studentId,
                        personDao = personDao,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            composable(NavItem.Schedule.route) {
                ScheduleScreen(
                    onStartScheduling = { navController.navigate("schedulingConfig") },
                    onNavigateToResult = { navController.navigate("scheduleResult") }
                )
            }

            composable("schedulingConfig") {
                SchedulingConfigScreen(
                    personDao = personDao,
                    onNavigateBack = { navController.popBackStack() },
                    onSchedulingComplete = { result ->
                        scheduleViewModel.setScheduleResult(result)
                        navController.navigate("scheduleResult") {
                            popUpTo("schedulingConfig") { inclusive = true }
                        }
                    }
                )
            }

            composable("scheduleResult") {
                val result by scheduleViewModel.scheduleResult.collectAsState()
                result?.let {
                    ScheduleResultScreen(
                        result = it,
                        onNavigateBack = { navController.popBackStack() }
                    )
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有可显示的排班结果")
                }
            }

            composable(NavItem.Settings.route) {
                SettingsScreen(
                    onNavigateToAbout = { navController.navigate("about") }
                )
            }

            composable("about") {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataImportScreen(
    personDao: PersonDao,
    onNavigateToDetail: (studentId: String) -> Unit
) {
    val context = LocalContext.current
    val peopleList by personDao.getAllPeople().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var parsingMessage by remember { mutableStateOf<String?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    // 使用 nullable Person 来存储将要被删除的人员信息
    var personToDelete by remember { mutableStateOf<Person?>(null) }

    // 注意：内部的 processExcelStream 函数已被移除，我们将调用全局的 processSingleExcelFile
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { selectedUri ->
                parsingMessage = "正在解析单个文件..."
                scope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(selectedUri)?.use {
                            val peopleFromFile = processSingleExcelFile(it) // 调用全局函数
                            personDao.upsertAll(peopleFromFile)
                        }
                        withContext(Dispatchers.Main) {
                            parsingMessage = "单个文件导入完成。"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            parsingMessage = "无法打开文件: ${e.message}"
                        }
                    }
                }
            }
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            treeUri?.let {
                parsingMessage = "正在批量处理文件夹..."
                scope.launch(Dispatchers.IO) {
                    val documentTree = DocumentFile.fromTreeUri(context, it)
                    val allPeopleFromFiles = mutableListOf<Person>()
                    var totalFileCount = 0

                    documentTree?.listFiles()?.forEach { file ->
                        if (file.isFile && file.name?.endsWith(".xlsx") == true) {
                            totalFileCount++
                            try {
                                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                                    allPeopleFromFiles.addAll(processSingleExcelFile(inputStream)) // 调用全局函数
                                }
                            } catch (e: Exception) {
                                Log.e("BatchImport", "无法打开文件 ${file.name}", e)
                            }
                        }
                    }

                    val finalPeopleMap = allPeopleFromFiles
                        .groupBy { it.studentId }
                        .mapValues { (_, people) ->
                            Person(
                                studentId = people.first().studentId,
                                name = people.first().name,
                                allClassTimes = people.joinToString(";") { it.allClassTimes }
                            )
                        }
                    val peopleToUpsert = finalPeopleMap.values.toList()

                    if (peopleToUpsert.isNotEmpty()) {
                        val existingIds = personDao.getAllStudentIds()
                        var newCount = 0
                        var updatedCount = 0

                        peopleToUpsert.forEach { person ->
                            if (existingIds.contains(person.studentId)) {
                                updatedCount++
                            } else {
                                newCount++
                            }
                        }
                        personDao.upsertAll(peopleToUpsert)
                        withContext(Dispatchers.Main) {
                            parsingMessage = "批量导入完成！共扫描 $totalFileCount 个文件。\n新增 $newCount 人，更新 $updatedCount 人。"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            parsingMessage = "在 $totalFileCount 个文件中未找到有效数据。"
                        }
                    }
                }
            }
        }
    )

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = isFabMenuExpanded) {
                    Column(horizontalAlignment = Alignment.End) {
                        SmallFloatingActionButton(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // 优先匹配 .xlsx
                                        "application/vnd.ms-excel", // 顺便支持老的 .xls 格式
                                        "*/*"  // --- 关键：添加这个通配符，允许选择所有类型的文件 ---
                                    )
                                )
                                isFabMenuExpanded = false
                            },
                            content = { Icon(Icons.Default.FileOpen, contentDescription = "单个导入") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SmallFloatingActionButton(
                            onClick = {
                                folderPickerLauncher.launch(null)
                                isFabMenuExpanded = false
                            },
                            content = { Icon(Icons.Default.FolderOpen, contentDescription = "批量导入") }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                FloatingActionButton(
                    onClick = { isFabMenuExpanded = !isFabMenuExpanded }
                ) {
                    Icon(
                        if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "主操作按钮"
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            parsingMessage?.let {
                item { Text(text = it, modifier = Modifier.padding(16.dp)) }
            }
            if (peopleList.isEmpty() && parsingMessage == null) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "数据库为空，请点击右下角按钮导入数据。")
                    }
                }
            } else {
                items(peopleList) { person ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { onNavigateToDetail(person.studentId) }
                    ) {
                        // --- 核心修改点 ---
                        ListItem(
                            headlineContent = { Text(person.name, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(person.studentId, style = MaterialTheme.typography.bodyMedium) },
                            // ↓↓↓ 新增：在列表项的尾部添加一个删除图标按钮 ↓↓↓
                            trailingContent = {
                                IconButton(onClick = {
                                    // 点击按钮时，记录下要删除的人，并弹出对话框
                                    personToDelete = person
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "删除 ${person.name}",
                                        tint = MaterialTheme.colorScheme.error // 使用错误颜色提示危险操作
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    // --- 新增：删除确认对话框 ---
    personToDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { personToDelete = null }, // 点击对话框外部时，关闭对话框
            title = { Text("确认删除") },
            text = { Text("确定要删除人员 “${person.name} (${person.studentId})” 吗？\n此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            personDao.deletePersonById(person.studentId)
                        }
                        personToDelete = null // 关闭对话框
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) // 确认按钮也使用警示色
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { personToDelete = null }) { // 取消按钮
                    Text("取消")
                }
            }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onStartScheduling: () -> Unit, onNavigateToResult: () -> Unit) {
    val context = LocalContext.current
    val historyDao = AppDatabase.getDatabase(context).scheduleHistoryDao()
    val historyList by historyDao.getAllHistory().collectAsState(initial = emptyList())
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val scope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排班历史") },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空历史记录")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onStartScheduling) {
                Icon(Icons.Default.DateRange, contentDescription = "开始排班")
            }
        }
    ) { innerPadding ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无排班历史，请点击右下角按钮开始排班。")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(historyList) { history ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                val type = object : TypeToken<List<StorableScheduleEntry>>() {}.type
                                val storableList: List<StorableScheduleEntry> = Gson().fromJson(history.scheduleJson, type)
                                val scheduleResult: ScheduleResult = storableList.associate { it.shift to it.people }
                                scheduleViewModel.setScheduleResult(scheduleResult)
                                onNavigateToResult()
                            },
                    ) {
                        ListItem(
                            headlineContent = { Text(formatTimestamp(history.timestamp)) },
                            leadingContent = { Icon(Icons.Default.History, contentDescription = "历史记录") }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要删除所有排班历史记录吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            historyDao.clearAll()
                        }
                        showClearDialog = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateToAbout: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val shiftsSet by settingsManager.shiftsFlow.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    var showAddShiftDialog by remember { mutableStateOf(false) }

    val shifts = remember(shiftsSet) {
        shiftsSet.mapNotNull {
            try {
                val parts = it.split(',')
                val day = DayOfWeek.valueOf(parts[0])
                val start = parts[1].toInt()
                val end = parts[2].toInt()
                Shift(id = "${day}_${start}_${end}", dayOfWeek = day, sessions = start..end)
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.dayOfWeek.value }, { it.sessions.first }))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            item {
                Text("班次规划", style = MaterialTheme.typography.titleLarge)
                Text(
                    "在这里定义每周需要安排的班次。修改将自动保存。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(shifts) { shift ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${shift.dayOfWeek.toDisplayName()} 第${shift.sessions.first}-${shift.sessions.last}节",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val updatedShifts = shiftsSet.toMutableSet()
                        updatedShifts.remove("${shift.dayOfWeek},${shift.sessions.first},${shift.sessions.last}")
                        scope.launch { settingsManager.saveShifts(updatedShifts) }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除班次")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showAddShiftDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加新班次")
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                ListItem(
                    headlineContent = { Text("关于") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "关于") },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout)
                )
            }
        }
    }

    if (showAddShiftDialog) {
        var selectedDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
        var startSession by remember { mutableStateOf("") }
        var endSession by remember { mutableStateOf("") }
        var isDayMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddShiftDialog = false },
            title = { Text("添加新班次") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = isDayMenuExpanded,
                        onExpandedChange = { isDayMenuExpanded = !isDayMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDay.toDisplayName(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("星期") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDayMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isDayMenuExpanded,
                            onDismissRequest = { isDayMenuExpanded = false }
                        ) {
                            DayOfWeek.entries.forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toDisplayName()) },
                                    onClick = {
                                        selectedDay = day
                                        isDayMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startSession,
                            onValueChange = { if (it.all { c -> c.isDigit() }) startSession = it },
                            label = { Text("开始节次") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endSession,
                            onValueChange = { if (it.all { c -> c.isDigit() }) endSession = it },
                            label = { Text("结束节次") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val start = startSession.toIntOrNull()
                        val end = endSession.toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            val newShiftString = "$selectedDay,$start,$end"
                            val updatedShifts = shiftsSet.toMutableSet()
                            updatedShifts.add(newShiftString)
                            scope.launch { settingsManager.saveShifts(updatedShifts) }
                            showAddShiftDialog = false
                        }
                    },
                    enabled = startSession.isNotEmpty() && endSession.isNotEmpty()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddShiftDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    studentId: String,
    personDao: PersonDao,
    onNavigateBack: () -> Unit
) {
    val personState = produceState<Person?>(initialValue = null, key1 = studentId) {
        value = personDao.getPersonById(studentId)
    }

    val person = personState.value
    var name by remember(person) { mutableStateOf(person?.name ?: "") }
    var allClassTimes by remember(person) { mutableStateOf(person?.allClassTimes ?: "") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人员详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(onClick = {
                        person?.let {
                            val updatedPerson = it.copy(
                                name = name,
                                allClassTimes = allClassTimes
                            )
                            scope.launch(Dispatchers.IO) {
                                personDao.upsert(updatedPerson)
                            }
                            onNavigateBack()
                        }
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { /* 学号不允许修改 */ },
                    label = { Text("学号 (不可修改)") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = allClassTimes,
                    onValueChange = { allClassTimes = it },
                    label = { Text("上课时间") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingConfigScreen(
    personDao: PersonDao,
    onNavigateBack: () -> Unit,
    onSchedulingComplete: (ScheduleResult) -> Unit
) {
    val allPeople by personDao.getAllPeople().collectAsState(initial = emptyList())
    val selectedPeople = remember { mutableStateMapOf<String, Boolean>() }
    val gradeRules = remember { mutableStateListOf<GradeRule>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settingsManager = remember { SettingsManager(context) }
    val shiftsSet by settingsManager.shiftsFlow.collectAsState(initial = emptySet())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排班配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val selectedPeopleList = allPeople.filter { selectedPeople[it.studentId] == true }
                    val currentGradeRules = gradeRules.toList()
                    scope.launch(Dispatchers.Default) {

                        val customShifts = shiftsSet.mapNotNull {
                            try {
                                val parts = it.split(',')
                                val day = DayOfWeek.valueOf(parts[0])
                                val start = parts[1].toInt()
                                val end = parts[2].toInt()
                                Shift(id = "${day}_${start}_${end}", dayOfWeek = day, sessions = start..end)
                            } catch (_: Exception) { null }
                        }

                        val result = Scheduler.generateSchedule(
                            allParticipants = selectedPeopleList,
                            gradeRules = currentGradeRules,
                            shifts = customShifts
                        )

                        if (result.isNotEmpty()) {
                            val storableList = result.map { (shift, people) ->
                                StorableScheduleEntry(shift, people)
                            }
                            val jsonResult = Gson().toJson(storableList)
                            val historyDao = AppDatabase.getDatabase(context).scheduleHistoryDao()
                            val history = ScheduleHistory(
                                timestamp = System.currentTimeMillis(),
                                scheduleJson = jsonResult
                            )
                            historyDao.insert(history)
                        }

                        withContext(Dispatchers.Main) {
                            onSchedulingComplete(result)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = selectedPeople.any { it.value }
            ) {
                Text("生成排班表")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("1. 选择参与排班的人员", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val areAllSelected = allPeople.isNotEmpty() && allPeople.all { selectedPeople[it.studentId] == true }
                        allPeople.forEach { person -> selectedPeople[person.studentId] = !areAllSelected }
                    }
                ) {
                    Checkbox(
                        checked = allPeople.isNotEmpty() && allPeople.all { selectedPeople[it.studentId] == true },
                        onCheckedChange = { isChecked ->
                            allPeople.forEach { person -> selectedPeople[person.studentId] = isChecked }
                        }
                    )
                    Text("全选/全不选")
                }
            }
            items(allPeople) { person ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val currentSelection = selectedPeople[person.studentId] ?: false
                        selectedPeople[person.studentId] = !currentSelection
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedPeople[person.studentId] ?: false,
                        onCheckedChange = { isChecked -> selectedPeople[person.studentId] = isChecked }
                    )
                    Text(text = "${person.name} (${person.studentId})")
                }
            }

            item {
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2. 年级规则与配额", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { gradeRules.add(GradeRule()) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加年级规则")
                    }
                }
            }

            items(gradeRules.size) { index ->
                val rule = gradeRules[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = rule.grade,
                                onValueChange = { newGrade ->
                                    if (newGrade.all { it.isDigit() }) {
                                        gradeRules[index] = rule.copy(grade = newGrade)
                                    }
                                },
                                label = { Text("年级 (如: 23)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            IconButton(onClick = { gradeRules.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除规则")
                            }
                        }
                        OutlinedTextField(
                            value = rule.shiftsPerWeek,
                            onValueChange = { newCount ->
                                if (newCount.all { it.isDigit() }) {
                                    gradeRules[index] = rule.copy(shiftsPerWeek = newCount)
                                }
                            },
                            label = { Text("每周应排班次") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("需要高年级陪同")
                            Switch(
                                checked = rule.needsSeniorBuddy,
                                onCheckedChange = { isChecked ->
                                    gradeRules[index] = rule.copy(needsSeniorBuddy = isChecked)
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("允许值晚班")
                            Switch(
                                checked = rule.canDoNightShift,
                                onCheckedChange = { isChecked ->
                                    gradeRules[index] = rule.copy(canDoNightShift = isChecked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleResultScreen(
    result: ScheduleResult,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument-spreadsheetml.sheet"),
        onResult = { uri ->
            uri?.let { outputUri ->
                try {
                    context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        ExcelExporter.export(result, outputStream)
                    }
                    Log.d("ExcelExport", "文件成功保存到: $outputUri")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("ExcelExport", "文件保存失败", e)
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排班结果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val fileName = "值班表_${System.currentTimeMillis()}.xlsx"
                fileSaverLauncher.launch(fileName)
            }) {
                Icon(Icons.Default.Download, contentDescription = "导出为Excel")
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            item { Text("排班表", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp)) }

            val daysOfWeek = result.keys.map { it.dayOfWeek }.distinct().sorted()
            val shiftsByDay = result.keys.groupBy { it.dayOfWeek }

            if(daysOfWeek.isEmpty()){
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center){
                        Text("排班结果为空。")
                    }
                }
            }

            daysOfWeek.forEach { day ->
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = day.toDisplayName(),
                                style = MaterialTheme.typography.titleLarge
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = DividerDefaults.Thickness,
                                color = DividerDefaults.color
                            )

                            val dayShifts = shiftsByDay[day]?.sortedBy { it.sessions.first } ?: emptyList()

                            if (dayShifts.isEmpty()) {
                                Text("本日无排班")
                            } else {
                                dayShifts.forEachIndexed { index, shift ->
                                    val people = result[shift] ?: emptyList()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val sessionText = "${shift.sessions.first}-${shift.sessions.last}节"
                                            Text(
                                                text = sessionText,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            sessionTimeMap[sessionText]?.let { time ->
                                                Text(
                                                    text = time,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (people.isNotEmpty()) people.joinToString("、") { it.person.name } else "【空缺】",
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(2f),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    if (index < dayShifts.size - 1) {
                                        HorizontalDivider(
                                            Modifier,
                                            DividerDefaults.Thickness,
                                            DividerDefaults.color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.avatar), // Make sure you have an 'avatar.png' or .jpg in your res/drawable folder
                contentDescription = "作者头像",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("版本 v1.0", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    uriHandler.openUri("https://github.com/ApolloMonasa/ScheduleAssistant") // Please replace with your actual repo URL
                }
            ) {
                ListItem(
                    headlineContent = { Text("GitHub 仓库") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = "GitHub") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("联系邮箱") },
                    supportingContent = { Text("2784761245@qq.com") },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = "邮箱") }
                )
            }
        }
    }
}

private val sessionTimeMap = mapOf(
    "1-2节" to "8:00-9:40",
    "3-5节" to "10:00-12:00",
    "6-7节" to "13:30-15:00",
    "8-9节" to "15:20-17:00",
    "10-11节" to "18:00-20:00"
)

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun DayOfWeek.toDisplayName(): String {
    return this.getDisplayName(TextStyle.FULL, Locale.CHINESE)
}