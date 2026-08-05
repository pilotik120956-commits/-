// === ТЕСТОВИЙ КОД ===
package com.example.minicrm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph

// ========== 1. МОДЕЛІ ДАНИХ ==========
@Entity
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val phone: String,
    val company: String? = null
)

@Entity
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val name: String,
    val description: String,
    val budget: Double,
    val deadline: Long
)

// ========== 2. ROOM DAO ==========
@Dao
interface ClientDao {
    @Query("SELECT * FROM client")
    fun getAll(): Flow<List<Client>>
    
    @Insert
    suspend fun insert(client: Client)
    
    @Delete
    suspend fun delete(client: Client)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project WHERE clientId = :clientId")
    fun getByClient(clientId: Int): Flow<List<Project>>
    
    @Insert
    suspend fun insert(project: Project)
}

@Database(entities = [Client::class, Project::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun projectDao(): ProjectDao
}

// ========== 3. REPOSITORY ==========
class Repository(private val db: AppDatabase) {
    fun getClients() = db.clientDao().getAll()
    suspend fun addClient(client: Client) = db.clientDao().insert(client)
    suspend fun deleteClient(client: Client) = db.clientDao().delete(client)
    
    fun getProjects(clientId: Int) = db.projectDao().getByClient(clientId)
    suspend fun addProject(project: Project) = db.projectDao().insert(project)
}

// ========== 4. VIEWMODEL ==========
class MainViewModel(private val repo: Repository) : androidx.lifecycle.ViewModel() {
    private val _selectedClientId = MutableStateFlow<Int?>(null)
    val selectedClientId: StateFlow<Int?> = _selectedClientId.asStateFlow()
    
    private val _selectedProjectId = MutableStateFlow<Int?>(null)
    val selectedProjectId: StateFlow<Int?> = _selectedProjectId.asStateFlow()
    
    fun selectClient(id: Int?) { _selectedClientId.value = id }
    fun selectProject(id: Int?) { _selectedProjectId.value = id }
    
    fun getClients() = repo.getClients()
    suspend fun addClient(client: Client) = repo.addClient(client)
    suspend fun deleteClient(client: Client) = repo.deleteClient(client)
    
    fun getProjects(clientId: Int) = repo.getProjects(clientId)
    suspend fun addProject(project: Project) = repo.addProject(project)
}

// ========== 5. ОСНОВНИЙ UI ==========
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "crm_database"
        ).build()
        
        val repo = Repository(db)
        
        setContent {
            MaterialTheme {
                val viewModel: MainViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.Factory {
                    MainViewModel(repo)
                })
                
                TabletCrmScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TabletCrmScreen(viewModel: MainViewModel) {
    val clients by viewModel.getClients().collectAsState(initial = emptyList())
    val selectedClientId by viewModel.selectedClientId.collectAsState()
    val selectedProjectId by viewModel.selectedProjectId.collectAsState()
    
    val projects = if (selectedClientId != null) {
        viewModel.getProjects(selectedClientId!!).collectAsState(initial = emptyList()).value
    } else emptyList()
    
    val selectedProject = projects.find { it.id == selectedProjectId }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // ===== КОЛОНКА 1: КЛІЄНТИ (25%) =====
        ClientListColumn(
            clients = clients,
            selectedClientId = selectedClientId,
            onClientClick = { viewModel.selectClient(it.id) },
            onAddClient = { name, email, phone, company ->
                viewModel.addClient(Client(name = name, email = email, phone = phone, company = company))
            },
            modifier = Modifier.weight(0.25f)
        )
        
        // ===== КОЛОНКА 2: ПРОЄКТИ (35%) =====
        AnimatedVisibility(
            visible = selectedClientId != null,
            enter = slideInHorizontally() + fadeIn(),
            exit = slideOutHorizontally() + fadeOut()
        ) {
            ProjectListColumn(
                projects = projects,
                selectedProjectId = selectedProjectId,
                onProjectClick = { viewModel.selectProject(it.id) },
                onAddProject = { name, desc, budget, deadline ->
                    selectedClientId?.let { clientId ->
                        viewModel.addProject(Project(
                            clientId = clientId,
                            name = name,
                            description = desc,
                            budget = budget,
                            deadline = deadline
                        ))
                    }
                },
                modifier = Modifier.weight(0.35f)
            )
        }
        
        // ===== КОЛОНКА 3: ДЕТАЛІ (40%) =====
        AnimatedVisibility(
            visible = selectedProjectId != null,
            enter = slideInHorizontally() + fadeIn(),
            exit = slideOutHorizontally() + fadeOut()
        ) {
            DetailColumn(
                project = selectedProject,
                client = clients.find { it.id == selectedProject?.clientId },
                onGenerateInvoice = { project, client ->
                    val context = LocalContext.current
                    generateInvoice(context, project, client)
                },
                modifier = Modifier.weight(0.4f)
            )
        }
    }
}

// ========== 6. КОМПОНЕНТИ UI ==========
@Composable
fun ClientListColumn(
    clients: List<Client>,
    selectedClientId: Int?,
    onClientClick: (Client) -> Unit,
    onAddClient: (String, String, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Text("Клієнти", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(clients) { client ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { onClientClick(client) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedClientId == client.id) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(client.name, style = MaterialTheme.typography.titleMedium)
                            Text(client.email, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Додати клієнта")
            }
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новий клієнт") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ім'я") })
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") })
                    OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Компанія") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onAddClient(name, email, phone, company.takeIf { it.isNotBlank() })
                        showDialog = false
                        name = ""; email = ""; phone = ""; company = ""
                    }
                }) { Text("Зберегти") }
            },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("Скасувати") } }
        )
    }
}

@Composable
fun ProjectListColumn(
    projects: List<Project>,
    selectedProjectId: Int?,
    onProjectClick: (Project) -> Unit,
    onAddProject: (String, String, Double, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Text("Проєкти", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(projects) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { onProjectClick(project) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProjectId == project.id) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text("$${project.budget}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Додати проєкт")
            }
        }
    }
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новий проєкт") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Опис") })
                    OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("Бюджет") })
                    OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Дедлайн (timestamp)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && budget.toDoubleOrNull() != null) {
                        onAddProject(name, desc, budget.toDouble(), deadline.toLongOrNull() ?: System.currentTimeMillis())
                        showDialog = false
                        name = ""; desc = ""; budget = ""; deadline = ""
                    }
                }) { Text("Зберегти") }
            },
            dismissButton = { Button(onClick = { showDialog = false }) { Text("Скасувати") } }
        )
    }
}

@Composable
fun DetailColumn(
    project: Project?,
    client: Client?,
    onGenerateInvoice: (Project, Client) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
        if (project != null && client != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Деталі проєкту", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Проєкт: ${project.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Клієнт: ${client.name}")
                        Text("Опис: ${project.description}")
                        Text("Бюджет: $${project.budget}")
                        Text("Дедлайн: ${SimpleDateFormat("dd.MM.yyyy").format(Date(project.deadline))}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onGenerateInvoice(project, client) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Text("Згенерувати рахунок (PDF)")
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Text("Оберіть проєкт", modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        }
    }
}

// ========== 7. ГЕНЕРАЦІЯ PDF ==========
fun generateInvoice(context: android.content.Context, project: Project, client: Client) {
    try {
        val pdfFile = File(context.filesDir, "Invoice_${project.id}_${System.currentTimeMillis()}.pdf")
        val writer = PdfWriter(pdfFile.path)
        val pdf = PdfDocument(writer)
        val document = Document(pdf, com.itextpdf.kernel.geom.PageSize.A4)
        
        document.add(Paragraph("INVOICE").setFontSize(20).setBold())
        document.add(Paragraph(" "))
        document.add(Paragraph("Client: ${client.name}"))
        document.add(Paragraph("Company: ${client.company ?: "N/A"}"))
        document.add(Paragraph("Project: ${project.name}"))
        document.add(Paragraph("Amount: $${project.budget}"))
        document.add(Paragraph("Due: ${SimpleDateFormat("dd.MM.yyyy").format(Date(project.deadline))}"))
        
        document.close()
        
        // Відкриваємо PDF
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
