package com.metapurge.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metapurge.app.domain.model.ImageItem
import com.metapurge.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isDarkMode: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val viewModel: MainViewModel = viewModel()
    
    val imagesState by viewModel.images.collectAsState()
    val statsState by viewModel.stats.collectAsState()
    val isProcessingState by viewModel.isProcessing.collectAsState()
    val toastState by viewModel.toast.collectAsState()

    val backgroundColor = if (isDarkMode) DarkNavy else Color(0xFFF8FAFC)
    val surfaceColor = if (isDarkMode) DarkNavyLight else White
    val textColor = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark
    val cardColor = if (isDarkMode) DarkNavyLight else White
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.processUris(uris)
        }
    }

    var showInfoModal by remember { mutableStateOf(false) }

    LaunchedEffect(toastState) {
        if (toastState != null) {
            Toast.makeText(context, toastState, Toast.LENGTH_SHORT).show()
            delay(2000)
            viewModel.dismissToast()
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "MetaPurge",
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                            tint = textSecondary
                        )
                    }
                    IconButton(onClick = { showInfoModal = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = SkyBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            StatsRow(
                filesPurged = statsState.filesPurged,
                dataRemoved = viewModel.formatBytes(statsState.dataRemoved),
                gpsFound = statsState.gpsFound,
                isDarkMode = isDarkMode
            )

            Spacer(modifier = Modifier.height(16.dp))

            UploadZone(
                onClick = { launcher.launch("image/jpeg") },
                isDarkMode = isDarkMode
            )

            AnimatedVisibility(
                visible = imagesState.isNotEmpty(),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                BatchActions(
                    count = imagesState.count { !it.isPurged && it.metadata?.hasExif == true },
                    isProcessing = isProcessingState,
                    onPurgeAll = { viewModel.purgeAll() },
                    onClear = { viewModel.clearAll() },
                    isDarkMode = isDarkMode
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(imagesState, key = { it.id }) { image ->
                    ImageCard(
                        image = image,
                        onPurge = { viewModel.purgeImage(image.id) },
                        formatBytes = viewModel::formatBytes,
                        isDarkMode = isDarkMode
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        if (showInfoModal) {
            InfoModal(onDismiss = { showInfoModal = false }, isDarkMode = isDarkMode)
        }
    }
}

@Composable
private fun StatsRow(
    filesPurged: Int,
    dataRemoved: String,
    gpsFound: Int,
    isDarkMode: Boolean
) {
    val surfaceColor = if (isDarkMode) DarkNavyLight else White
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            value = filesPurged.toString(),
            label = "Files Purged",
            modifier = Modifier.weight(1f),
            isDarkMode = isDarkMode
        )
        StatCard(
            value = dataRemoved,
            label = "Data Removed",
            modifier = Modifier.weight(1f),
            isDarkMode = isDarkMode
        )
        StatCard(
            value = gpsFound.toString(),
            label = "GPS Found",
            modifier = Modifier.weight(1f),
            isDarkMode = isDarkMode
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean
) {
    val surfaceColor = if (isDarkMode) DarkNavyLight else White
    val textPrimary = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = SkyBlue
            )
            Text(
                label,
                fontSize = 12.sp,
                color = textSecondary
            )
        }
    }
}

@Composable
private fun UploadZone(onClick: () -> Unit, isDarkMode: Boolean) {
    val cardColor = if (isDarkMode) DarkNavyLight else White
    val textPrimary = if (isDarkMode) White else DarkNavy
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SkyBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = SkyBlue
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Select Photos",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .background(SkyBlue.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = SkyBlue
                )
                Text(
                    "100% Offline",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SkyBlue
                )
            }
        }
    }
}

@Composable
private fun BatchActions(
    count: Int,
    isProcessing: Boolean,
    onPurgeAll: () -> Unit,
    onClear: () -> Unit,
    isDarkMode: Boolean
) {
    val textOnPrimary = if (isDarkMode) White else DarkNavy
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPurgeAll,
            modifier = Modifier.weight(1f),
            enabled = count > 0 && !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = SkyBlue,
                disabledContainerColor = SkyBlue.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = textOnPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Purge All ($count)", color = textOnPrimary, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onClear,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun ImageCard(
    image: ImageItem,
    onPurge: () -> Unit,
    formatBytes: (Long) -> String,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "rotation"
    )
    
    val cardColor = if (isDarkMode) DarkNavyLight else White
    val textPrimary = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(image.uri))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (image.isPurged) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SkyBlue.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "CLEANED",
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        image.name,
                        fontWeight = FontWeight.SemiBold,
                        color = White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatBytes(image.size),
                        fontSize = 12.sp,
                        color = White.copy(alpha = 0.8f)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (image.metadata?.hasExif != true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SkyBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SkyBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "No metadata found - Image is clean",
                            fontWeight = FontWeight.Medium,
                            color = SkyBlue
                        )
                    }
                } else {
                    val metadata = image.metadata!!
                    val totalTags = metadata.allTags.image.size + metadata.allTags.exif.size + metadata.allTags.gps.size

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDarkMode) DarkNavy.copy(alpha = 0.3f) else Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SkyBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "$totalTags metadata tags",
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary
                                    )
                                    metadata.gps?.let {
                                        Text(
                                            "GPS Location detected",
                                            fontSize = 12.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            metadata.dateTime?.let {
                                MetadataRow(icon = Icons.Default.Schedule, label = "Date", value = it, isDarkMode = isDarkMode)
                            }
                            metadata.gps?.let {
                                MetadataRow(icon = Icons.Default.LocationOn, label = "Location", value = it.display, isDarkMode = isDarkMode)
                            }
                            metadata.camera?.let {
                                MetadataRow(icon = Icons.Default.PhoneAndroid, label = "Device", value = it, isDarkMode = isDarkMode)
                            }
                            metadata.software?.let {
                                MetadataRow(icon = Icons.Default.Code, label = "Software", value = it, isDarkMode = isDarkMode)
                            }
                        }

                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.rotate(rotationState),
                                tint = SkyBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (expanded) "Show Less" else "View All $totalTags Tags", color = SkyBlue)
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .background(if (isDarkMode) DarkNavy.copy(alpha = 0.2f) else Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (metadata.allTags.image.isNotEmpty()) {
                                    Text(
                                        "Image Info",
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    )
                                    metadata.allTags.image.forEach { (key, value) ->
                                        FullMetadataRow(key, value, isDarkMode)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (metadata.allTags.exif.isNotEmpty()) {
                                    Text(
                                        "EXIF Data",
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    )
                                    metadata.allTags.exif.forEach { (key, value) ->
                                        FullMetadataRow(key, value, isDarkMode)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (metadata.allTags.gps.isNotEmpty()) {
                                    Text(
                                        "GPS Data",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
                                        fontSize = 14.sp
                                    )
                                    metadata.allTags.gps.forEach { (key, value) ->
                                        FullMetadataRow(key, value, isDarkMode)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPurge,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !image.isPurged && image.metadata?.hasExif == true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyBlue,
                        disabledContainerColor = SlateGray
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        if (image.isPurged) Icons.Default.Check else Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (image.isPurged) SlateGray else DarkNavy
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (image.isPurged) "Already Cleaned" else "Remove Metadata",
                        color = if (image.isPurged) SlateGray else DarkNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    label: String,
    value: String,
    isDarkMode: Boolean
) {
    val textPrimary = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SkyBlue,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(label, fontSize = 11.sp, color = textSecondary)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
        }
    }
}

@Composable
private fun FullMetadataRow(key: String, value: String, isDarkMode: Boolean) {
    val textPrimary = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            key.replace("_", " "),
            fontSize = 12.sp,
            color = textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            modifier = Modifier.weight(1.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoModal(onDismiss: () -> Unit, isDarkMode: Boolean) {
    val cardColor = if (isDarkMode) DarkNavyLight else White
    val textPrimary = if (isDarkMode) White else DarkNavy
    val textSecondary = if (isDarkMode) SlateGray else SlateDark
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "About MetaPurge",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Every photo contains hidden EXIF metadata - including GPS location, device info, and timestamps. MetaPurge removes all of it, keeping only the pixels.",
                fontSize = 14.sp,
                color = textSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SkyBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = SkyBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "100% Offline - Your photos never leave your device",
                    fontWeight = FontWeight.Medium,
                    color = SkyBlue
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Got it!", color = DarkNavy, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
