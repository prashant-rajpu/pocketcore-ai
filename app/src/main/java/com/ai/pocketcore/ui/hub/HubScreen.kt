package com.ai.pocketcore.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.ui.hub.components.CuratedModelCard
import com.ai.pocketcore.ui.hub.components.ModelCard
import com.ai.pocketcore.ui.hub.components.QuantizationBottomSheet
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary

/**
 * Hub Screen featuring Samsung One UI bottom-weighted ergonomic reachability:
 * - Upper viewing zone: Screen title and curated flagship picks carousel
 * - Lower interaction zone: Natural thumb-reach search bar, quick filter chips, and search results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val selectedModel by viewModel.selectedModelForTree.collectAsState()
    val quantizationFiles by viewModel.quantizationFiles.collectAsState()
    val isLoadingTree by viewModel.isLoadingTree.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val activeDownloadFileName by viewModel.activeDownloadFileName.collectAsState()
    val curatedModels by viewModel.curatedModels.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PitchBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // One UI Split: Upper Viewing Zone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiTokens.PaddingMedium, vertical = OneUiTokens.PaddingSmall)
            ) {
                Text(
                    text = "Model Hub",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Hugging Face · Optimized for Snapdragon 8 Elite",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )
            }

            // Viewing Zone Carousel: Curated Flagship Models
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = OneUiTokens.PaddingMedium),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(curatedModels, key = { it.repoId }) { model ->
                    CuratedModelCard(
                        curated = model,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.selectModel(model)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lower Interaction Zone: Search Bar & Filter Chips (Easily reachable with one hand)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiTokens.PaddingMedium)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                    placeholder = {
                        Text(
                            text = "Search GGUF models on Hugging Face...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyberCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            viewModel.search()
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface,
                        disabledContainerColor = CardSurface,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyberCyan
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Filter Chips
                val filterOptions = listOf("All", "Recommended", "7B", "3B", "Reasoning")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterOptions.forEach { filter ->
                        val isSelected = if (filter == "All") searchQuery.isBlank() else selectedFilter == filter && searchQuery.isNotBlank()
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFilter = filter
                                when (filter) {
                                    "All" -> viewModel.onQueryChanged("")
                                    "Recommended" -> viewModel.onQueryChanged("Qwen2.5-7B")
                                    "7B" -> viewModel.onQueryChanged("7B")
                                    "3B" -> viewModel.onQueryChanged("3B")
                                    "Reasoning" -> viewModel.onQueryChanged("DeepSeek-R1")
                                }
                            },
                            label = {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            },
                            shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CardSurface,
                                labelColor = TextSecondary,
                                selectedContainerColor = ElectricCobalt,
                                selectedLabelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = CardBorder,
                                selectedBorderColor = CyberCyan
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results / Feed Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = OneUiTokens.PaddingMedium)
            ) {
                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(32.dp))
                                Text(
                                    text = "Searching Hugging Face models...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    searchError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = searchError ?: "An error occurred",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                                IconButton(onClick = { viewModel.search() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = CyberCyan
                                    )
                                }
                            }
                        }
                    }
                    searchQuery.isNotBlank() && searchResults.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No GGUF models found for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                    searchResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(searchResults, key = { it.repoId }) { model ->
                                val isFlagship = curatedModels.any { it.repoId == model.repoId }
                                ModelCard(
                                    model = model,
                                    isRecommended = isFlagship,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.selectModel(model)
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        // Empty search query state: Show Curated Flagship recommendations list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            item {
                                Text(
                                    text = "Recommended for Galaxy S25 (12GB RAM)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(curatedModels, key = { it.repoId }) { curated ->
                                ModelCard(
                                    model = curated.toHfModelSummary(),
                                    isRecommended = true,
                                    subtitle = curated.description,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.selectModel(curated)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet: Quantization Picker
    if (selectedModel != null) {
        QuantizationBottomSheet(
            model = selectedModel!!,
            files = quantizationFiles,
            isLoading = isLoadingTree,
            downloadState = downloadState,
            activeDownloadFileName = activeDownloadFileName,
            onDismiss = { viewModel.dismissTree() },
            onDownload = { file -> viewModel.downloadQuantization(file) },
            onPauseDownload = { viewModel.pauseDownload() },
            onResumeDownload = { viewModel.resumeDownload() },
            isFileDownloaded = { fileName -> viewModel.isModelDownloaded(fileName) }
        )
    }
}
