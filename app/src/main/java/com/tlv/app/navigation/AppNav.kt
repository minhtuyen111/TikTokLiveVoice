package com.tlv.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import com.tlv.app.ui.BounceIcon
import com.tlv.app.ui.Text
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tlv.app.Graph
import com.tlv.app.ui.*
import kotlinx.coroutines.launch

private val AUTH_ROUTES = setOf("splash", "login", "register", "forgot", "reset")
private val PUBLIC_PAGES = setOf("terms", "privacy")
private val SETTINGS_GROUP = setOf("settings", "filters", "appearance", "notifications", "security", "account", "memes", "admin")

private data class Dest(val route: String, val title: String, val icon: ImageVector)

private val DRAWER = listOf(
    Dest("home", "Tổng quan", Icons.Filled.Dashboard),
    Dest("connect", "Kết nối TikTok LIVE", Icons.Filled.Link),
    Dest("comments", "Bình luận trực tiếp", Icons.Filled.Forum),
    Dest("voice", "Giọng đọc", Icons.Filled.RecordVoiceOver),
    Dest("memes", "Âm thanh meme", Icons.Filled.MusicNote),
    Dest("plans", "Gói dịch vụ", Icons.Filled.WorkspacePremium),
    Dest("wallet", "Ví & nạp tiền", Icons.Filled.AccountBalanceWallet),
    Dest("filters", "Bộ lọc bình luận", Icons.Filled.FilterAlt),
    Dest("history", "Lịch sử hoạt động", Icons.Filled.History),
    Dest("account", "Quản lý tài khoản", Icons.Filled.AccountCircle),
    Dest("security", "Bảo mật", Icons.Filled.Security),
    Dest("appearance", "Giao diện", Icons.Filled.Palette),
    Dest("notifications", "Thông báo", Icons.Filled.Notifications),
    Dest("help", "Trợ giúp", Icons.Filled.HelpOutline),
    Dest("terms", "Điều khoản sử dụng", Icons.Filled.Description),
    Dest("privacy", "Chính sách quyền riêng tư", Icons.Filled.PrivacyTip),
    Dest("about", "Giới thiệu ứng dụng", Icons.Filled.Info),
)

private val ADMIN = Dest("admin", "Quản trị", Icons.Filled.AdminPanelSettings)

private val BOTTOM = listOf(
    Dest("home", "Trang chủ", Icons.Filled.Home),
    Dest("comments", "Bình luận", Icons.Filled.Forum),
    Dest("voice", "Giọng đọc", Icons.Filled.RecordVoiceOver),
    Dest("settings", "Cài đặt", Icons.Filled.Settings),
)

private fun titleOf(route: String) = when (route) {
    "settings" -> "Cài đặt"
    "admin" -> ADMIN.title
    else -> DRAWER.firstOrNull { it.route == route }?.title ?: "Voice Live TikTok"
}

private fun NavHostController.goTop(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "splash"
    val session by Graph.auth.session.collectAsState()
    val token by Graph.recoveryToken.collectAsState()
    val settings by Graph.settings.collectAsState()
    val ent by Graph.entitlement.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(scope, SnackbarHostState()) }
    var confirmLogout by remember { mutableStateOf(false) }

    val chrome = session != null && route !in AUTH_ROUTES
    val publicPage = session == null && route in PUBLIC_PAGES

    LaunchedEffect(session?.userId) { if (session != null) Graph.auth.refreshProfile() } // cập nhật vai trò từ máy chủ
    LaunchedEffect(token) { if (token != null) nav.navigate("reset") { launchSingleTop = true } }
    LaunchedEffect(session, route) {
        if (session == null && route !in AUTH_ROUTES && route !in PUBLIC_PAGES) {
            nav.navigate("login") { popUpTo(0) { inclusive = true }; launchSingleTop = true }
        }
    }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

    fun go(r: String) { scope.launch { drawer.close() }; nav.goTop(r) }

    val slow = settings.reduceMotion
    CompositionLocalProvider(LocalToaster provides toaster) {
        ModalNavigationDrawer(
            drawerState = drawer, gesturesEnabled = chrome,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogoMark(48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(session?.username?.ifEmpty { "Người dùng" } ?: "Người dùng", fontWeight = FontWeight.Bold)
                                Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                TierBadge(ent?.tier ?: "free")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        (if (session?.isAdmin == true) DRAWER + ADMIN else DRAWER).forEach { d ->
                            NavigationDrawerItem(
                                label = { Text(d.title) }, icon = { Icon(d.icon, null) },
                                selected = route == d.route, onClick = { go(d.route) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationDrawerItem(
                            label = { Text("Đăng xuất") }, icon = { Icon(Icons.Filled.Logout, null) },
                            selected = false, onClick = { scope.launch { drawer.close() }; confirmLogout = true },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (chrome) ScaffoldDefaults.contentWindowInsets else WindowInsets(0),
                snackbarHost = { ToastHost(toaster) },
                topBar = {
                    if (chrome || publicPage) CenterAlignedTopAppBar(
                        title = { Text(titleOf(route), fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            // Trang gốc: nút menu. Trang con: nút quay lại (menu chuyển sang bên phải).
                            if (chrome && BOTTOM.any { it.route == route }) AnimIconButton(Icons.Filled.Menu, "Mở menu", { scope.launch { drawer.open() } })
                            else Box(Modifier.padding(start = 10.dp)) { BackButton({ if (!nav.popBackStack()) nav.goTop("home") }) }
                        },
                        actions = {
                            if (chrome && BOTTOM.none { it.route == route }) AnimIconButton(Icons.Filled.Menu, "Mở menu", { scope.launch { drawer.open() } })
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    )
                },
                bottomBar = {
                    if (chrome) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        BOTTOM.forEach { b ->
                            val selected = route == b.route || (b.route == "settings" && route in SETTINGS_GROUP)
                            NavigationBarItem(selected, { go(b.route) }, { BounceIcon(b.icon, b.title, selected) }, label = { Text(b.title) })
                        }
                    }
                },
            ) { pad ->
                NavHost(
                    nav, startDestination = "splash", modifier = Modifier.padding(pad),
                    // Chuyển cảnh nhẹ: mờ dần + phóng rất nhẹ (chỉ đổi alpha/scale trong lớp đồ họa, không đo lại bố cục).
                    enterTransition = { if (slow) fadeIn(tween(1)) else fadeIn(tween(200, 60, FastOutSlowInEasing)) + scaleIn(tween(240, 40, FastOutSlowInEasing), 0.97f) },
                    exitTransition = { if (slow) fadeOut(tween(1)) else fadeOut(tween(110, easing = FastOutLinearInEasing)) },
                    popEnterTransition = { if (slow) fadeIn(tween(1)) else fadeIn(tween(200, 60, FastOutSlowInEasing)) + scaleIn(tween(240, 40, FastOutSlowInEasing), 0.97f) },
                    popExitTransition = { if (slow) fadeOut(tween(1)) else fadeOut(tween(110, easing = FastOutLinearInEasing)) + scaleOut(tween(160), 0.98f) },
                ) {
                    composable("splash") {
                        SplashScreen { ok ->
                            nav.navigate(if (ok) "home" else "login") { popUpTo("splash") { inclusive = true } }
                        }
                    }
                    composable("login") {
                        LoginScreen(
                            onLoggedIn = { nav.navigate("home") { popUpTo("login") { inclusive = true } } },
                            onRegister = { nav.navigate("register") },
                            onForgot = { nav.navigate("forgot") },
                        )
                    }
                    composable("register") {
                        Box(Modifier.fillMaxSize()) {
                            RegisterScreen({ nav.popBackStack() }, { nav.navigate("terms") }, { nav.navigate("privacy") })
                            BackButton({ nav.popBackStack() }, Modifier.align(Alignment.TopStart).systemBarsPadding().padding(14.dp))
                        }
                    }
                    composable("forgot") {
                        Box(Modifier.fillMaxSize()) {
                            ForgotScreen { nav.popBackStack() }
                            BackButton({ nav.popBackStack() }, Modifier.align(Alignment.TopStart).systemBarsPadding().padding(14.dp))
                        }
                    }
                    composable("reset") {
                        val t = token
                        if (t == null) LaunchedEffect(Unit) { nav.navigate("login") { popUpTo(0) } }
                        else ResetScreen(t) { nav.navigate("login") { popUpTo(0) { inclusive = true } } }
                    }

                    composable("home") { HomeScreen({ nav.navigate("connect") }, { go("comments") }, { go("voice") }) }
                    composable("connect") { ConnectScreen() }
                    composable("comments") { CommentsScreen() }
                    composable("voice") { VoiceScreen() }
                    composable("memes") { MemeScreen() }
                    composable("plans") { PlansScreen(onTopUp = { nav.navigate("wallet") { launchSingleTop = true } }) }
                    composable("wallet") { WalletScreen() }
                    composable("admin") { AdminScreen() }
                    composable("filters") { FilterScreen() }
                    composable("history") { HistoryScreen() }
                    composable("account") { AccountScreen({ confirmLogout = true }, { nav.navigate("security") }) }
                    composable("security") {
                        SecurityScreen { nav.navigate("login") { popUpTo(0) { inclusive = true } } }
                    }
                    composable("appearance") { AppearanceScreen() }
                    composable("notifications") { NotificationsScreen() }
                    composable("settings") { SettingsHub { nav.navigate(it) { launchSingleTop = true } } }
                    composable("help") { HelpScreen() }
                    composable("terms") { TermsScreen() }
                    composable("privacy") { PrivacyScreen() }
                    composable("about") { AboutScreen() }
                }
            }
        }

        if (confirmLogout) AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất?") },
            text = { Text(if (Graph.engine.active) "Phiên LIVE đang chạy sẽ bị dừng." else "Bạn sẽ cần đăng nhập lại.") },
            confirmButton = {
                TextButton({
                    confirmLogout = false
                    scope.launch {
                        Graph.logout()
                        nav.navigate("login") { popUpTo(0) { inclusive = true } }
                    }
                }) { Text("Đăng xuất") }
            },
            dismissButton = { TextButton({ confirmLogout = false }) { Text("Hủy") } },
        )
    }
}
