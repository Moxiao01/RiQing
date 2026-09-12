package com.riqing

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.riqing.core.datastore.AppSettings
import com.riqing.core.datastore.SettingsDataStore
import com.riqing.core.datastore.ThemeMode
import com.riqing.core.designsystem.RiQingTheme
import com.riqing.ui.agent.AgentRoute
import com.riqing.ui.calendar.CalendarRoute
import com.riqing.ui.course.CourseEditRoute
import com.riqing.ui.event.EventEditRoute
import com.riqing.ui.home.HomeRoute
import com.riqing.ui.importer.CourseImportRoute
import com.riqing.ui.settings.MeRoute
import com.riqing.ui.settings.SettingsAiRoute
import com.riqing.ui.settings.SettingsNotifyRoute
import com.riqing.ui.settings.SettingsThemeRoute
import com.riqing.ui.settings.SemesterEditRoute
import com.riqing.ui.settings.SemesterListRoute
import com.riqing.ui.timetable.TimetableRoute
import com.riqing.ui.todo.TodoEditRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Calendar : Dest("calendar")
    data object Timetable : Dest("timetable")
    data object Agent : Dest("agent")
    data object Me : Dest("me")
    data object EventEdit : Dest("event_edit?eventId={eventId}&startAt={startAt}&endAt={endAt}") {
        fun create(eventId: String? = null, startAt: Long? = null, endAt: Long? = null): String {
            val parts = mutableListOf<String>()
            eventId?.let { parts.add("eventId=$it") }
            startAt?.let { parts.add("startAt=$it") }
            endAt?.let { parts.add("endAt=$it") }
            val q = parts.joinToString("&")
            return if (q.isEmpty()) "event_edit" else "event_edit?$q"
        }
    }
    data object TodoEdit : Dest("todo_edit?todoId={todoId}") {
        fun create(todoId: String? = null) =
            if (todoId == null) "todo_edit" else "todo_edit?todoId=$todoId"
    }
    data object CourseEdit : Dest("course_edit?courseId={courseId}") {
        fun create(courseId: String? = null) =
            if (courseId == null) "course_edit" else "course_edit?courseId=$courseId"
    }
    data object SemesterList : Dest("semester_list")
    data object CourseImport : Dest("course_import")
    data object SemesterEdit : Dest("semester_edit?semesterId={semesterId}") {
        fun create(semesterId: String? = null): String =
            if (semesterId == null) "semester_edit" else "semester_edit?semesterId=$semesterId"
    }
    data object SettingsAi : Dest("settings_ai")
    data object SettingsNotify : Dest("settings_notify")
    data object SettingsTheme : Dest("settings_theme")
}

private data class Tab(val dest: Dest, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Dest.Home, "今日", Icons.Filled.Home),
    Tab(Dest.Calendar, "日历", Icons.Filled.CalendarMonth),
    Tab(Dest.Timetable, "课表", Icons.Filled.School),
    Tab(Dest.Agent, "Agent", Icons.Filled.Chat),
    Tab(Dest.Me, "我的", Icons.Filled.Person),
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settings.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 手动选主题时状态栏/导航栏图标也要跟着切，否则深色页面上是深色图标
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }
            }
            RiQingTheme(darkTheme = darkTheme) {
                RiQingRoot(themeMode = settings.themeMode)
            }
        }
    }
}

@Composable
fun RiQingRoot(themeMode: ThemeMode) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showBar = tabs.any { currentRoute == it.dest.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.dest.route,
                            onClick = {
                                nav.navigate(tab.dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            // edge-to-edge 下 adjustResize 不生效，必须自行消费 IME inset，否则键盘会盖住下半屏内容
            modifier = Modifier.padding(padding).imePadding(),
        ) {
            composable(Dest.Home.route) {
                HomeRoute(
                    onOpenEvent = { id -> nav.navigate(Dest.EventEdit.create(eventId = id)) },
                    onOpenTodo = { id -> nav.navigate(Dest.TodoEdit.create(todoId = id)) },
                    onAddEvent = { start, end -> nav.navigate(Dest.EventEdit.create(startAt = start, endAt = end)) },
                    onAddTodo = { nav.navigate(Dest.TodoEdit.create()) },
                )
            }
            composable(Dest.Calendar.route) {
                CalendarRoute(
                    onOpenEvent = { id -> nav.navigate(Dest.EventEdit.create(eventId = id)) },
                    onAddEvent = { start, end -> nav.navigate(Dest.EventEdit.create(startAt = start, endAt = end)) },
                )
            }
            composable(Dest.Timetable.route) {
                TimetableRoute(
                    onOpenCourse = { id -> nav.navigate(Dest.CourseEdit.create(courseId = id)) },
                    onAddCourse = { nav.navigate(Dest.CourseEdit.create()) },
                )
            }
            composable(Dest.Agent.route) { AgentRoute() }
            composable(Dest.Me.route) {
                MeRoute(
                    themeMode = themeMode,
                    onAi = { nav.navigate(Dest.SettingsAi.route) },
                    onTheme = { nav.navigate(Dest.SettingsTheme.route) },
                    onSemester = { nav.navigate(Dest.SemesterList.route) },
                    onImport = { nav.navigate(Dest.CourseImport.route) },
                    onNotify = { nav.navigate(Dest.SettingsNotify.route) },
                )
            }
            composable(
                route = Dest.EventEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("eventId") { nullable = true; defaultValue = null },
                    androidx.navigation.navArgument("startAt") { nullable = true; defaultValue = null },
                    androidx.navigation.navArgument("endAt") { nullable = true; defaultValue = null },
                ),
            ) {
                EventEditRoute(onDone = { nav.popBackStack() })
            }
            composable(
                route = Dest.TodoEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("todoId") { nullable = true; defaultValue = null },
                ),
            ) {
                TodoEditRoute(onDone = { nav.popBackStack() })
            }
            composable(
                route = Dest.CourseEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("courseId") { nullable = true; defaultValue = null },
                ),
            ) {
                CourseEditRoute(onDone = { nav.popBackStack() })
            }
            composable(Dest.SemesterList.route) {
                SemesterListRoute(
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate(Dest.SemesterEdit.create(id)) },
                )
            }
            composable(Dest.CourseImport.route) {
                CourseImportRoute(
                    onBack = { nav.popBackStack() },
                    onOpenSemester = { nav.navigate(Dest.SemesterList.route) },
                )
            }
            composable(
                route = Dest.SemesterEdit.route,
                arguments = listOf(
                    androidx.navigation.navArgument("semesterId") { nullable = true; defaultValue = null },
                ),
            ) {
                SemesterEditRoute(onDone = { nav.popBackStack() })
            }
            composable(Dest.SettingsAi.route) {
                SettingsAiRoute(onDone = { nav.popBackStack() })
            }
            composable(Dest.SettingsNotify.route) {
                SettingsNotifyRoute(onDone = { nav.popBackStack() })
            }
            composable(Dest.SettingsTheme.route) {
                SettingsThemeRoute(onDone = { nav.popBackStack() })
            }
        }
    }
}
