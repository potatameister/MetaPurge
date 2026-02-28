package com.metapurge.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metapurge.app.domain.model.ImageItem
import com.metapurge.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel()
) {
    val images by mainViewModel.images.collectAsState()
    val stats by mainViewModel.stats.collectAsState()
    val isProcessing by mainViewModel.isProcessing.collectAsState()
    val toast by mainViewModel.toast.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            mainViewModel.processUris(uris)
        }
    }

    var showInfoModal by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2000)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Header(
                onInfoClick = { showInfoModal = true }
            )

            StatsRow(
                filesPurged = stats.filesPurged,
                dataRemoved = viewModel.formatBytes(stats.dataRemoved),
                gpsFound = stats.gpsFound
            )

            UploadZone(
                onClick = { launcher.launch("image/jpeg") }
            )

            AnimatedVisibility(
                visible = images.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BatchActions(
                    count = images.count { !it.isPurged && it.metadata?.hasExif == true },
                    isProcessing = isProcessing,
                    onPurgeAll = { viewModel.purgeAll() },
                    onClear = { viewModel.clearAll() }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(images, key = { it.id }) { image ->
                    ImageCard(
                        image = image,
                        onPurge = { viewModel.purgeImage(image.id) },
                        formatBytes = viewModel::formatBytes
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Toast(message = toast ?: "")
        }

        if (showInfoModal) {
            InfoModal(onDismiss = { showInfoModal = false })
        }
    }
}

@Composable
private fun Header(onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Accent, AccentSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    "MetaPurge",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Privacy Tool",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        IconButton(onClick = onInfoClick) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Info",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun StatsRow(
    filesPurged: Int,
    dataRemoved: String,
    gpsFound: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            value = filesPurged.toString(),
            label = "Files Purged",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = dataRemoved,
            label = "Data Removed",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = gpsFound.toString(),
            label = "GPS Found",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BgSecondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Accent
            )
            Text(
                label,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun UploadZone(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = BgSecondary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(BgTertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Tap to Select Photos",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                "JPEG files",
                fontSize = 13.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .background(SuccessBg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Success
                )
                Text(
                    "100% Offline",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Success
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
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPurgeAll,
            modifier = Modifier.weight(1f),
            enabled = count > 0 && !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Purge All ($count)")
        }

        OutlinedButton(
            onClick = onClear,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun ImageCard(
    image: ImageItem,
    onPurge: () -> Unit,
    formatBytes: (Long) -> String
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSecondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 100f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        image.name,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            formatBytes(image.size),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        if (image.metadata?.hasExif == true) {
                            Text(
                                "• ${formatBytes(image.metadata.metadataSize)} metadata",
                                fontSize = 13.sp,
                                color = Accent
                            )
                        } else {
                            Text(
                                "• Clean",
                                fontSize = 13.sp,
                                color = Success
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (image.metadata?.hasExif != true) {
                    AlertItem(
                        icon = Icons.Default.CheckCircle,
                        message = "No metadata found! This image is clean.",
                        type = "success"
                    )
                } else {
                    AlertItem(
                        icon = Icons.Default.Warning,
                        message = "Hidden metadata detected! (${image.metadata.allTags.image.size + image.metadata.allTags.exif.size + image.metadata.allTags.gps.size} tags)",
                        type = "danger"
                    )

                    image.metadata.gps?.let { gps ->
                        GpsBox(
                            display = gps.display,
                            mapUrl = gps.mapUrl
                        )
                    }

                    image.metadata.camera?.let { camera ->
                        MetadataRow(
                            icon = Icons.Default.PhoneAndroid,
                            label = "Device",
                            value = camera
                        )
                    }

                    image.metadata.dateTime?.let { date ->
                        MetadataRow(
                            icon = Icons.Default.Schedule,
                            label = "Date Taken",
                            value = date
                        )
                    }

                    image.metadata.software?.let { software ->
                        MetadataRow(
                            icon = Icons.Default.Code,
                            label = "Software",
                            value = software
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPurge,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !image.isPurged && image.metadata?.hasExif == true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (image.isPurged) Success else Accent,
                        disabledContainerColor = BgTertiary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (image.isPurged) Icons.Default.Check else Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (image.isPurged) "Purged!" else "PURGE METADATA")
                }
            }
        }
    }
}

@Composable
private fun AlertItem(
    icon: ImageVector,
    message: String,
    type: String
) {
    val (bgColor, textColor) = when (type) {
        "success" -> SuccessBg to Success
        else -> DangerBg to Accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
        Text(message, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = textColor)
    }

    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun GpsBox(display: String, mapUrl: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DangerBg, RoundedCornerShape(12.dp))
            .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "GPS LOCATION FOUND",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Accent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            display,
            fontSize = 13.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            "⚠️ This reveals where the photo was taken!",
            fontSize = 11.sp,
            color = Accent
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgTertiary, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AccentSecondary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentSecondary, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun Toast(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgSecondary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = TextPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoModal(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Accent, AccentSecondary))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text("About MetaPurge", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("How it works & why it matters", fontSize = 12.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "What is Photo Metadata?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Every photo contains hidden data called EXIF metadata. Your camera or phone embeds this automatically — including GPS location, device model, and when the photo was taken.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DangerBg, RoundedCornerShape(12.dp))
                    .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("Why should you care?", fontWeight = FontWeight.SemiBold, color = Accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• GPS coordinates can reveal your home or daily routine\n• Device info can be used to track or identify you\n• Timestamps show exactly when and where you were", fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SuccessBg, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("Privacy guarantee", fontWeight = FontWeight.SemiBold, color = Success)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 100% offline — no internet needed\n• Nothing is uploaded anywhere\n• All processing happens on your device", fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BgTertiary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got it!")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "MetaPurge v1.0.0",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
