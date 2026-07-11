package com.example.webdavplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.webdavplayer.ui.browse.BrowseScreen
import com.example.webdavplayer.ui.player.PlayerScreen
import com.example.webdavplayer.ui.player.PlayerViewModel
import com.example.webdavplayer.ui.playlist.PlaylistScreen
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.servers.ServerConfigScreen
import com.example.webdavplayer.ui.servers.ServerListScreen
import com.example.webdavplayer.ui.settings.SettingsScreen
import com.example.webdavplayer.ui.theme.WebDavPlayerTheme

/** 应用根：主题 + 导航宿主 + 跨屏共享的播放器/播放列表状态。 */
@Composable
fun AppRoot() {
    WebDavPlayerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val navController: NavHostController = rememberNavController()
            // 跨 Browse / Player / Playlist 共享（Activity 作用域）
            val playerVm: PlayerViewModel = hiltViewModel()
            val playlistVm: PlaylistViewModel = hiltViewModel()

            NavHost(navController = navController, startDestination = "servers") {
                composable("servers") {
                    ServerListScreen(navController, playerVm, playlistVm)
                }
                composable(
                    route = "server_config?serverId={serverId}",
                    arguments = listOf(
                        navArgument("serverId") {
                            nullable = true
                            defaultValue = null
                            type = NavType.StringType
                        },
                    ),
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getString("serverId")
                    ServerConfigScreen(navController, serverId)
                }
                composable(
                    route = "browse/{serverId}?path={path}",
                    arguments = listOf(
                        navArgument("serverId") { type = NavType.StringType },
                        navArgument("path") {
                            nullable = true
                            defaultValue = null
                            type = NavType.StringType
                        },
                    ),
                ) { backStackEntry ->
                    BrowseScreen(navController, playerVm, playlistVm, backStackEntry)
                }
                composable("player") {
                    PlayerScreen(navController, playerVm, playlistVm)
                }
                composable("playlist") {
                    PlaylistScreen(navController, playerVm, playlistVm)
                }
                composable("settings") {
                    SettingsScreen(navController, playerVm, playlistVm)
                }
            }
        }
    }
}
