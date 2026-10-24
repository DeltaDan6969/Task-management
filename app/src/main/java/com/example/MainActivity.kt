package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import com.example.data.UserEntity
import com.example.ui.*
import com.example.ui.components.BottomNavBar
import com.example.ui.components.PinPadDialog
import com.example.ui.components.TabletNavRail
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var onUserInteractionListener: (() -> Unit)? = null

    override fun onUserInteraction() {
        super.onUserInteraction()
        onUserInteractionListener?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val syncManager = com.example.data.FirestoreSyncManager(
            context = applicationContext,
            database = database,
            scope = lifecycleScope
        )
        val repository = TaskRepository(database.taskDao(), syncManager)

        setContent {
            MyApplicationTheme {
                val viewModel: TaskTrackerViewModel = viewModel(
                    factory = TaskTrackerViewModelFactory(repository)
                )

                val context = LocalContext.current
                val focusManager = LocalFocusManager.current
                val keyboardController = LocalSoftwareKeyboardController.current
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val activeScreen by viewModel.activeScreen.collectAsStateWithLifecycle()
                val adminTab by viewModel.adminTab.collectAsStateWithLifecycle()
                val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
                val users by viewModel.users.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val tasks by viewModel.tasks.collectAsStateWithLifecycle()
                val storeConfigs by viewModel.storeConfigs.collectAsStateWithLifecycle()
                val completions by viewModel.completionsForSelectedDate.collectAsStateWithLifecycle()
                val notes by viewModel.notesForSelectedDate.collectAsStateWithLifecycle()
                val photos by viewModel.photosForSelectedDate.collectAsStateWithLifecycle()
                val allCompletions by viewModel.allCompletions.collectAsStateWithLifecycle()
                val allPhotos by viewModel.allPhotos.collectAsStateWithLifecycle()
                val reportStats by viewModel.reportStats.collectAsStateWithLifecycle()
                val outstandingDaysMap by viewModel.outstandingDaysMap.collectAsStateWithLifecycle()
                val isCloudConnected by viewModel.isCloudConnected.collectAsStateWithLifecycle()
                val cloudUserEmail by viewModel.cloudUserEmail.collectAsStateWithLifecycle()
                val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

                val storeName = storeConfigs["store_name"] ?: "ONESTOP CANTERBURY"
                val tagline = storeConfigs["tagline"] ?: "SIGN IN TO START YOUR SHIFT"

                // Inactivity tracking for 5-minute auto log out
                var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

                // Activity-level touch listener ensures user interactions in ANY dialog or window reset the timer
                DisposableEffect(Unit) {
                    onUserInteractionListener = {
                        lastInteractionTime = System.currentTimeMillis()
                    }
                    onDispose {
                        onUserInteractionListener = null
                    }
                }

                // 5-minute auto log out when user is signed in
                LaunchedEffect(currentUser) {
                    if (currentUser != null) {
                        lastInteractionTime = System.currentTimeMillis()
                        while (true) {
                            delay(5_000L)
                            val idleMillis = System.currentTimeMillis() - lastInteractionTime
                            if (idleMillis >= 5 * 60 * 1000L) { // 5 minutes inactivity
                                viewModel.signOut()
                                Toast.makeText(
                                    context,
                                    "Logged out due to 5 minutes of inactivity",
                                    Toast.LENGTH_LONG
                                ).show()
                                break
                            }
                        }
                    }
                }

                // Check admin authorization dialog if staff tries to open Admin
                var showAdminPinCheck by remember { mutableStateOf(false) }

                BackHandler(enabled = currentUser != null) {
                    if (activeScreen != AppScreen.TODAY) {
                        viewModel.navigateTo(AppScreen.TODAY)
                    } else {
                        viewModel.signOut()
                    }
                }

                if (currentUser == null || activeScreen == AppScreen.SIGN_IN) {
                    SignInScreen(
                        storeName = storeName,
                        tagline = tagline,
                        users = users,
                        isCloudConnected = isCloudConnected,
                        cloudUserEmail = cloudUserEmail,
                        syncStatus = syncStatus,
                        onSignInGoogle = {
                            viewModel.signInGoogle(this@MainActivity) { success, msg ->
                                Toast.makeText(
                                    context,
                                    if (success) "Google Cloud Connected: $msg" else "Google Sign-In: $msg",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onSignOutGoogle = {
                            viewModel.signOutGoogle()
                            Toast.makeText(context, "Google Cloud Disconnected", Toast.LENGTH_SHORT).show()
                        },
                        onAttemptSignIn = { userId, pin, onSuccess, onError ->
                            viewModel.attemptSignIn(
                                userId = userId,
                                pin = pin,
                                onSuccess = {
                                    onSuccess()
                                    Toast.makeText(context, "Shift started", Toast.LENGTH_SHORT).show()
                                },
                                onError = onError
                            )
                        },
                        onCompleteFirstLoginPin = { userId, newPin, onSuccess ->
                            viewModel.completeFirstLoginPin(
                                userId = userId,
                                newPin = newPin,
                                onSuccess = {
                                    onSuccess()
                                    Toast.makeText(context, "PIN set successfully. Welcome to your shift!", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                } else {
                    val activeUser = currentUser!!

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentUser) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                )
                            }
                    ) {
                        val isTablet = maxWidth >= 540.dp

                        if (isTablet) {
                            // Tablet Layout with Left Navigation Rail
                            Row(modifier = Modifier.fillMaxSize()) {
                                TabletNavRail(
                                    currentScreen = activeScreen,
                                    currentUser = activeUser,
                                    storeName = storeName,
                                    onScreenSelected = { targetScreen ->
                                        if (targetScreen == AppScreen.ADMIN && activeUser.role != "ADMIN") {
                                            showAdminPinCheck = true
                                        } else {
                                            viewModel.navigateTo(targetScreen)
                                        }
                                    },
                                    onSignOut = { viewModel.signOut() }
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .statusBarsPadding()
                                        .navigationBarsPadding()
                                ) {
                                    ActiveScreenContent(
                                        activeScreen = activeScreen,
                                        activeUser = activeUser,
                                        storeName = storeName,
                                        tagline = tagline,
                                        users = users,
                                        categories = categories,
                                        tasks = tasks,
                                        storeConfigs = storeConfigs,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        allCompletions = allCompletions,
                                        allPhotos = allPhotos,
                                        reportStats = reportStats,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedDate = selectedDate,
                                        adminTab = adminTab,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        } else {
                            // Phone fallback with Bottom Navigation Bar
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                bottomBar = {
                                    BottomNavBar(
                                        currentScreen = activeScreen,
                                        onScreenSelected = { targetScreen ->
                                            if (targetScreen == AppScreen.ADMIN && activeUser.role != "ADMIN") {
                                                showAdminPinCheck = true
                                            } else {
                                                viewModel.navigateTo(targetScreen)
                                            }
                                        }
                                    )
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    ActiveScreenContent(
                                        activeScreen = activeScreen,
                                        activeUser = activeUser,
                                        storeName = storeName,
                                        tagline = tagline,
                                        users = users,
                                        categories = categories,
                                        tasks = tasks,
                                        storeConfigs = storeConfigs,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        allCompletions = allCompletions,
                                        allPhotos = allPhotos,
                                        reportStats = reportStats,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedDate = selectedDate,
                                        adminTab = adminTab,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }

                    // Admin Access Prompt for Staff
                    if (showAdminPinCheck) {
                        val adminUser = users.firstOrNull { it.role == "ADMIN" } ?: activeUser
                        var pinError by remember { mutableStateOf<String?>(null) }
                        PinPadDialog(
                            user = adminUser,
                            title = "ADMIN PIN REQUIRED",
                            errorMessage = pinError,
                            onPinEntered = { enteredPin ->
                                viewModel.attemptSignIn(
                                    userId = adminUser.id,
                                    pin = enteredPin,
                                    onSuccess = {
                                        showAdminPinCheck = false
                                        viewModel.navigateTo(AppScreen.ADMIN)
                                    },
                                    onError = {
                                        pinError = "Admin PIN incorrect"
                                    }
                                )
                            },
                            onDismiss = {
                                showAdminPinCheck = false
                                pinError = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveScreenContent(
    activeScreen: AppScreen,
    activeUser: UserEntity,
    storeName: String,
    tagline: String,
    users: List<UserEntity>,
    categories: List<com.example.data.CategoryEntity>,
    tasks: List<com.example.data.TaskEntity>,
    storeConfigs: Map<String, String>,
    completions: List<com.example.data.TaskCompletionEntity>,
    notes: List<com.example.data.TaskNoteEntity>,
    photos: List<com.example.data.TaskPhotoEntity>,
    allCompletions: List<com.example.data.TaskCompletionEntity>,
    allPhotos: List<com.example.data.TaskPhotoEntity>,
    reportStats: ReportStats,
    outstandingDaysMap: Map<Long, Int>,
    selectedDate: String,
    adminTab: AdminTab,
    viewModel: TaskTrackerViewModel
) {
    val context = LocalContext.current
    when (activeScreen) {
        AppScreen.SIGN_IN -> {
            SignInScreen(
                storeName = storeName,
                tagline = tagline,
                users = users,
                onAttemptSignIn = { uId, pin, onSucc, onErr ->
                    viewModel.attemptSignIn(uId, pin, onSucc, onErr)
                },
                onCompleteFirstLoginPin = { uId, newPin, onSucc ->
                    viewModel.completeFirstLoginPin(uId, newPin, onSucc)
                }
            )
        }
        AppScreen.TODAY -> {
            TodayScreen(
                currentUser = activeUser,
                selectedDate = selectedDate,
                categories = categories,
                tasks = tasks,
                completions = completions,
                notes = notes,
                photos = photos,
                outstandingDaysMap = outstandingDaysMap,
                onToggleTask = { task, catName ->
                    viewModel.toggleTask(task, catName)
                },
                onAddNote = { taskId, text ->
                    viewModel.addNote(taskId, text)
                },
                onDeleteNote = { noteId ->
                    viewModel.deleteNote(noteId)
                },
                onAddPhoto = { taskId, uri ->
                    viewModel.addPhoto(taskId, uri)
                },
                onDeletePhoto = { photoId ->
                    viewModel.deletePhoto(photoId)
                },
                onPreviousDate = { viewModel.previousDate() },
                onNextDate = { viewModel.nextDate() },
                onResetToToday = { viewModel.resetToToday() },
                onSignOut = { viewModel.signOut() },
                onAddTask = { catId, title, bullets, priority, daysOfWeek ->
                    viewModel.addTask(catId, title, bullets, priority, daysOfWeek)
                },
                onSubmitSubTasksSignOff = { task, catName, compList, outList, isPartial ->
                    viewModel.submitSubTasksSignOff(task, catName, compList, outList, isPartial)
                },
                onUpdateTask = { updated ->
                    viewModel.updateTask(updated)
                },
                onSkipTask = { task, catName, reason ->
                    viewModel.skipTask(task, catName, reason)
                }
            )
        }
        AppScreen.HISTORY -> {
            HistoryScreen(
                allCompletions = allCompletions,
                selectedDate = selectedDate,
                onSelectDate = { viewModel.setSelectedDate(it) }
            )
        }
        AppScreen.ADMIN -> {
            AdminScreen(
                currentUser = activeUser,
                currentTab = adminTab,
                onTabSelected = { viewModel.selectAdminTab(it) },
                users = users,
                categories = categories,
                tasks = tasks,
                storeConfigs = storeConfigs,
                reportStats = reportStats,
                allCompletions = allCompletions,
                allPhotos = allPhotos,
                onAddUser = { name, role, pin, onDone ->
                    viewModel.addUser(name, role, pin, onDone)
                },
                onUpdateUserPin = { uId, newPin, onDone ->
                    viewModel.updateUserPin(uId, newPin, onDone)
                },
                onDeleteUser = { uId ->
                    viewModel.deleteUser(uId)
                },
                onAddCategory = { name ->
                    viewModel.addCategory(name)
                },
                onDeleteCategory = { catId ->
                    viewModel.deleteCategory(catId)
                },
                onAddTask = { catId, title, bullets, priority, daysOfWeek ->
                    viewModel.addTask(catId, title, bullets, priority, daysOfWeek)
                },
                onUpdateTask = { updated ->
                    viewModel.updateTask(updated)
                },
                onDeleteTask = { tId ->
                    viewModel.deleteTask(tId)
                },
                onUpdateBranding = { newStore, newTag ->
                    viewModel.updateBranding(newStore, newTag)
                },
                onExportBackup = {
                    viewModel.exportBackup(context)
                },
                onRestoreBackup = { json, onDone, onError ->
                    viewModel.restoreBackup(json, onDone, onError)
                },
                onSignOut = { viewModel.signOut() }
            )
        }
    }
}
