package com.example.aicreationassistant.ui.imageedit

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.aicreationassistant.util.WatermarkPosition
import com.example.aicreationassistant.util.Constants
import com.yalantis.ucrop.UCrop
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditScreen(
    sourceUri: String,
    onNavigateBack: () -> Unit,
    onSaveCompleted: (String) -> Unit = {}
) {
    val viewModel: ImageEditViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sourceUri) {
        viewModel.init(sourceUri)
    }

    // UCrop launcher
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = UCrop.getOutput(result.data!!)
            uri?.let { viewModel.onCropResult(it) }
        }
    }

    fun launchCrop() {
        val source = state.croppedUri ?: Uri.parse(state.sourceUri)
        val destinationUri = Uri.fromFile(
            File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        )
        val uCropIntent = UCrop.of(source, destinationUri)
            .withAspectRatio(0f, 0f)
            .withMaxResultSize(2048, 2048)
            .getIntent(context)
        cropLauncher.launch(uCropIntent)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图片编辑") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 图片预览
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                val displayUri = state.currentBitmapUri ?: Uri.parse(state.sourceUri)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "编辑中的图片",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // 裁剪按钮
            OutlinedButton(
                onClick = { launchCrop() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("裁剪图片")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 水印设置
            Text(
                "文字水印",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = state.watermarkText,
                onValueChange = { viewModel.updateWatermarkText(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入水印文字") },
                singleLine = true
            )

            // 位置选择
            Text("位置", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkPosition.entries.take(3).forEach { pos ->
                    FilterChip(
                        selected = state.watermarkPosition == pos,
                        onClick = { viewModel.updateWatermarkPosition(pos) },
                        label = { Text(pos.label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkPosition.entries.drop(3).forEach { pos ->
                    FilterChip(
                        selected = state.watermarkPosition == pos,
                        onClick = { viewModel.updateWatermarkPosition(pos) },
                        label = { Text(pos.label) }
                    )
                }
            }

            // 透明度
            Text(
                "透明度: ${"%.0f".format(state.watermarkOpacity * 100)}%",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = state.watermarkOpacity,
                onValueChange = { viewModel.updateWatermarkOpacity(it) },
                valueRange = 0.1f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            // 应用水印按钮
            Button(
                onClick = { viewModel.applyWatermark(context) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.watermarkText.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("处理中…")
                } else {
                    Text("应用水印")
                }
            }

            // 保存和分享按钮
            if (state.processedUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val uri = state.processedUri ?: return@OutlinedButton
                            // 保存到系统相册
                            val savedUri = viewModel.saveToGallery(context, uri)
                            if (savedUri != null) {
                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                viewModel.markSaved()
                                // 回传编辑后的图片 URI
                                onSaveCompleted(savedUri.toString())
                            } else {
                                Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存")
                    }

                    OutlinedButton(
                        onClick = {
                            val uri = state.processedUri ?: return@OutlinedButton
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                Constants.FILE_PROVIDER_AUTHORITY,
                                File(uri.path ?: return@OutlinedButton)
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "分享图片"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享")
                    }
                }
            }
        }
    }
}
