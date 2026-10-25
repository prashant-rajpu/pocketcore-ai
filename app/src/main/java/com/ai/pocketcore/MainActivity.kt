package com.ai.pocketcore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.pocketcore.ui.hub.HubScreen
import com.ai.pocketcore.ui.hub.HubViewModel
import com.ai.pocketcore.ui.studio.StudioScreen
import com.ai.pocketcore.ui.studio.StudioViewModel
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTheme
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.vault.VaultScreen
import com.ai.pocketcore.ui.vault.VaultViewModel

enum class PocketCoreTab(
    val title: String,
    val icon: ImageVector
) {
    HUB("Hub", Icons.Default.Explore),
    VAULT("Vault", Icons.Default.Folder),
    STUDIO("Studio", Icons.AutoMirrored.Filled.Chat)
}

/**
 * Main launcher Activity for PocketCore AI.
 * Hosts the root navigation graph between Hub, Vault, and Studio screens.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PocketCoreApplication

        setContent {
            OneUiTheme {
                val (availableRam, totalRam) = remember { app.getMemoryInfo() }

                val hubViewModel: HubViewModel = viewModel(
                    factory = HubViewModel.provideFactory(
                        hfRepository = app.hfRepository,
                        downloadManager = app.downloadManager
                    )
                )

                val vaultViewModel: VaultViewModel = viewModel(
                    factory = VaultViewModel.provideFactory(
                        modelDao = app.database.modelDao(),
                        loadModelUseCase = app.loadModelUseCase,
                        modelsDir = app.modelsDirectory,
                        availableRamBytes = availableRam,
                        totalRamBytes = totalRam
                    )
                )

                val studioViewModel: StudioViewModel = viewModel(
                    factory = StudioViewModel.provideFactory(
                        streamChatUseCase = app.streamChatUseCase,
                        chatDao = app.database.chatDao(),
                        modelDao = app.database.modelDao(),
                        getRamUsageBytes = { app.getMemoryInfo().first }
                    )
                )

                PocketCoreApp(
                    hubViewModel = hubViewModel,
                    vaultViewModel = vaultViewModel,
                    studioViewModel = studioViewModel
                )
            }
        }
    }
}

@Composable
fun PocketCoreApp(
    hubViewModel: HubViewModel,
    vaultViewModel: VaultViewModel,
    studioViewModel: StudioViewModel
) {
    var currentTab by remember { mutableStateOf(PocketCoreTab.HUB) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = PitchBlack,
        bottomBar = {
            OneUiBottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    if (currentTab != tab) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentTab = tab
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PitchBlack)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { tab ->
                when (tab) {
                    PocketCoreTab.HUB -> HubScreen(
                        viewModel = hubViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    PocketCoreTab.VAULT -> VaultScreen(
                        viewModel = vaultViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    PocketCoreTab.STUDIO -> StudioScreen(
                        viewModel = studioViewModel,
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToVault = { currentTab = PocketCoreTab.VAULT }
                    )
                }
            }
        }
    }
}

/**
 * Samsung One UI Pro bottom navigation bar with 24dp squircle pill highlights,
 * pitch black OLED container, and ergonomic reachability.
 */
@Composable
fun OneUiBottomNavigationBar(
    currentTab: PocketCoreTab,
    onTabSelected: (PocketCoreTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PitchBlack,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = CardBorder.copy(alpha = 0.4f)
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketCoreTab.values().forEach { tab ->
                val isSelected = currentTab == tab

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) CardSurface else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(tab)
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) ElectricCobalt else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) TextPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
