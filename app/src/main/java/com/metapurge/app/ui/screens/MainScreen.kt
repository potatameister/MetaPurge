package com.metapurge.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metapurge.app.R
import com.metapurge.app.domain.model.ImageItem
import com.metapurge.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    val viewModel: MainViewModel = viewModel()
    val images by viewModel.images.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val toast by viewModel.toast.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.processUris(uris)
        }
    }

    var showInfoModal by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            delay(2000)
            viewModel.dismissToast()
        }
    }

    Scaffold(
        containerColor = LightGray,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = R.drawable.ic_launcher,
                            contentDescription = "MetaPurge Icon",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            "MetaPurge",
                            fontWeight = FontWeight.Bold,
                            color = White,
                            fontSize = 22.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkNavy,
                    titleContentColor = White
                ),
                actions = {
                    IconButton(onClick = { showInfoModal = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = SlateGray
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                AnimatedContent(
                    targetState = images.isNotEmpty(),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "upload_zone"
                ) { hasImages ->
                    if (hasImages) {
                        CompactUploadZone(
                            count = images.size,
                            onClick = { launcher.launch("image/jpeg") }
                        )
                    } else {
                        UploadZone(onClick = { launcher.launch("image/jpeg") })
                    }
                }
            }

            if (images.isNotEmpty()) {
                item {
                    BatchActions(
                        count = images.count { !it.isPurged && it.metadata?.hasExif == true },
                        isProcessing = isProcessing,
                        onPurgeAll = { viewModel.purgeAll() },
                        onClear = { viewModel.clearAll() }
                    )
                }
            }

            items(images, key = { it.id }) { image ->
                ImageCard(
                    image = image,
                    onPurge = { viewModel.purgeImage(image.id) },
                    formatBytes = viewModel::formatBytes
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SupportSection()
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        if (showInfoModal) {
            InfoModal(onDismiss = { showInfoModal = false })
        }
    }
}

@Composable
private fun UploadZone(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, DarkNavy.copy(alpha = 0.1f))
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
                    .background(DarkNavy),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Select Photos",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkNavy
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .background(DarkNavy, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = White
                )
                Text(
                    "100% Offline",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun CompactUploadZone(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkNavy.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkNavy.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        "Select More Photos",
                        fontWeight = FontWeight.SemiBold,
                        color = DarkNavy,
                        fontSize = 15.sp
                    )
                    Text(
                        "$count photo${if (count > 1) "s" else ""} selected",
                        fontSize = 12.sp,
                        color = SlateDark
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SlateGray
            )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPurgeAll,
            modifier = Modifier.weight(1f),
            enabled = count > 0 && !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkNavyLight,
                disabledContainerColor = DarkNavyLight.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Purge All ($count)", color = White, fontWeight = FontWeight.SemiBold)
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
    formatBytes: (Long) -> String
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = White),
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
                            .background(DarkNavy.copy(alpha = 0.9f)),
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
                            .background(DarkNavy, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "No metadata - Image is clean",
                            fontWeight = FontWeight.Medium,
                            color = White
                        )
                    }
                } else {
                    val metadata = image.metadata!!
                    val totalTags = (metadata.allTags.image.size + metadata.allTags.exif.size + metadata.allTags.gps.size)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkNavy.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
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
                                    tint = DarkNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        "$totalTags metadata tags",
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkNavy
                                    )
                                    metadata.gps?.let {
                                        Text(
                                            "GPS detected",
                                            fontSize = 12.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            metadata.dateTime?.let {
                                MetadataRow(icon = Icons.Default.Schedule, label = "Date", value = it)
                            }
                            metadata.gps?.let {
                                MetadataRow(icon = Icons.Default.LocationOn, label = "Location", value = it.display)
                            }
                            metadata.camera?.let {
                                MetadataRow(icon = Icons.Default.PhoneAndroid, label = "Device", value = it)
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
                                tint = DarkNavy
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (expanded) "Show Less" else "View All $totalTags Tags",
                                color = DarkNavy
                            )
                        }

                        AnimatedVisibility(visible = expanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(DarkNavy.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (metadata.allTags.image.isNotEmpty()) {
                                    Text(
                                        "Image Info",
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy,
                                        fontSize = 14.sp
                                    )
                                    metadata.allTags.image.forEach { (key, value) ->
                                        FullMetadataRow(key, value)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (metadata.allTags.exif.isNotEmpty()) {
                                    Text(
                                        "EXIF Data",
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy,
                                        fontSize = 14.sp
                                    )
                                    metadata.allTags.exif.forEach { (key, value) ->
                                        FullMetadataRow(key, value)
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
                                        FullMetadataRow(key, value)
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
                        containerColor = DarkNavy,
                        disabledContainerColor = SlateGray
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        if (image.isPurged) Icons.Default.Check else Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (image.isPurged) SlateGray else White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (image.isPurged) "Already Cleaned" else "Remove Metadata",
                        color = if (image.isPurged) SlateGray else White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = DarkNavy,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(label, fontSize = 11.sp, color = SlateDark)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DarkNavy)
        }
    }
}

@Composable
private fun FullMetadataRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            key.replace("_", " "),
            fontSize = 12.sp,
            color = SlateDark,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = DarkNavy,
            modifier = Modifier.weight(1.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoModal(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
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
                color = DarkNavy
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Every photo contains hidden EXIF metadata - including GPS location, device info, and timestamps. MetaPurge removes all of it, keeping only the pixels.",
                fontSize = 14.sp,
                color = SlateDark,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavy, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "100% Offline - Your photos never leave your device",
                    fontWeight = FontWeight.Medium,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Got it!", color = White, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SupportSection() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/potatameister"))
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(containerColor = SkyBlue.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = SkyBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Support MetaPurge",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkNavy
            )
        }
    }
}
