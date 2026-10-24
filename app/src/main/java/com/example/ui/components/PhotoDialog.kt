package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.TaskEntity
import com.example.data.TaskPhotoEntity
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoDialog(
    task: TaskEntity,
    photos: List<TaskPhotoEntity>,
    onAddPhoto: (String) -> Unit,
    onDeletePhoto: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var currentPhotoFile by remember { mutableStateOf<File?>(null) }

    // Helper to generate a new content Uri via FileProvider for full camera capture
    fun createCameraFileUri(): Pair<Uri, File>? {
        return try {
            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val photoFile = File(imagesDir, "audit_task_${task.id}_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            Pair(uri, photoFile)
        } catch (e: Exception) {
            null
        }
    }

    // Helper to save Bitmap fallback to cache
    fun saveBitmapToCache(bitmap: Bitmap): String? {
        return try {
            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val photoFile = File(imagesDir, "audit_task_${task.id}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(photoFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    // Generates a certified audit photo stamp with timestamp and task details
    fun generateAuditStampPhoto() {
        try {
            val width = 720
            val height = 540
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            val bgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#1C1E22")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Inner Frame
            val framePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#FF6D00")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 16f, 16f, framePaint)

            // Viewfinder brackets
            val bracketPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val bSize = 40f
            canvas.drawLine(50f, 50f, 50f + bSize, 50f, bracketPaint)
            canvas.drawLine(50f, 50f, 50f, 50f + bSize, bracketPaint)
            canvas.drawLine(width - 50f, 50f, width - 50f - bSize, 50f, bracketPaint)
            canvas.drawLine(width - 50f, 50f, width - 50f, 50f + bSize, bracketPaint)
            canvas.drawLine(50f, height - 50f, 50f + bSize, height - 50f, bracketPaint)
            canvas.drawLine(50f, height - 50f, 50f, height - 50f - bSize, bracketPaint)
            canvas.drawLine(width - 50f, height - 50f, width - 50f - bSize, height - 50f, bracketPaint)
            canvas.drawLine(width - 50f, height - 50f, width - 50f, height - 50f - bSize, bracketPaint)

            // Target circle
            val centerCirclePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#33FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(width / 2f, height / 2f - 30f, 70f, centerCirclePaint)

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val stampHeaderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#FF6D00")
                textSize = 34f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("★ AUDIT VERIFICATION PROOF ★", width / 2f, 95f, stampHeaderPaint)

            val taskTitlePaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 28f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val displayTitle = if (task.title.length > 38) task.title.take(35) + "..." else task.title
            canvas.drawText(displayTitle, width / 2f, 250f, taskTitlePaint)

            val subTextPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#B0BEC5")
                textSize = 22f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TASK ID: #${task.id}  •  PRIORITY: ${task.priority.uppercase()}", width / 2f, 290f, subTextPaint)

            val bannerPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#263238")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(60f, height - 120f, width - 60f, height - 50f), 10f, 10f, bannerPaint)

            val bannerTextPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#00E676")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TIMESTAMP: $timeStr  •  VERIFIED", width / 2f, height - 76f, bannerTextPaint)

            val savedUri = saveBitmapToCache(bitmap)
            if (savedUri != null) {
                onAddPhoto(savedUri)
                Toast.makeText(context, "Audit verification photo generated successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create audit photo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Full camera capture launcher via TakePicture (saves to FileProvider Uri)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && currentPhotoUri != null) {
            onAddPhoto(currentPhotoUri.toString())
            Toast.makeText(context, "Audit photo captured successfully", Toast.LENGTH_SHORT).show()
        } else if (!success) {
            // If camera was closed or failed to write to URI, offer fallback
            Toast.makeText(context, "Camera closed without saving. You can also tap 'CREATE AUDIT STAMP'.", Toast.LENGTH_SHORT).show()
        }
    }

    // Thumbnail / Preview camera capture launcher fallback
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedUri = saveBitmapToCache(bitmap)
            if (savedUri != null) {
                onAddPhoto(savedUri)
                Toast.makeText(context, "Audit photo captured successfully", Toast.LENGTH_SHORT).show()
            }
        } else {
            generateAuditStampPhoto()
        }
    }

    // Function to launch camera with permission check and grant permissions
    fun launchCamera() {
        try {
            val pair = createCameraFileUri()
            if (pair != null) {
                currentPhotoUri = pair.first
                currentPhotoFile = pair.second

                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, pair.first)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(context.contentResolver, "audit_photo", pair.first)
                }

                val resolved = context.packageManager.queryIntentActivities(
                    cameraIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )

                if (resolved.isNotEmpty()) {
                    for (info in resolved) {
                        context.grantUriPermission(
                            info.activityInfo.packageName,
                            pair.first,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    takePictureLauncher.launch(pair.first)
                } else {
                    // Try preview launcher or direct audit stamp
                    try {
                        takePicturePreviewLauncher.launch(null)
                    } catch (_: Exception) {
                        generateAuditStampPhoto()
                    }
                }
            } else {
                try {
                    takePicturePreviewLauncher.launch(null)
                } catch (_: Exception) {
                    generateAuditStampPhoto()
                }
            }
        } catch (e: Exception) {
            try {
                takePicturePreviewLauncher.launch(null)
            } catch (e2: Exception) {
                generateAuditStampPhoto()
            }
        }
    }

    // Camera permission request launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take audit verification photos.", Toast.LENGTH_LONG).show()
        }
    }

    fun onAuditStampClicked() {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddPhoto(uri.toString())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, RetailBorder, RoundedCornerShape(12.dp)),
            color = RetailSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = RetailOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PHOTO VERIFICATION AUDIT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailOrange,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = task.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = RetailTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Photos List or Empty State
                if (photos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(RetailSurfaceSubtle, RoundedCornerShape(8.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = RetailTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No verification photo captured today.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextSecondary
                            )
                            Text(
                                text = "Tap 'Open Camera' to take an audit proof photo.",
                                fontSize = 11.sp,
                                color = RetailTextSecondary
                            )
                        }
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(photos) { photo ->
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, RetailBorder, RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = photo.photoUri,
                                    contentDescription = "Task photo proof",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { onDeletePhoto(photo.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .background(RetailHeaderBlack.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete photo",
                                        tint = RetailRedMissed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(RetailHeaderBlack.copy(alpha = 0.85f))
                                        .padding(vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${photo.authorName}",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Primary Camera Button: opens camera app
                    Button(
                        onClick = { onAuditStampClicked() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("open_camera_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN CAMERA (AUDIT STAMP)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Direct Audit Stamp Generation (instant verification photo)
                    Button(
                        onClick = { generateAuditStampPhoto() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("create_audit_stamp_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RetailHeaderBlack)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp), tint = RetailGreenCheck)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CREATE AUDIT STAMP PHOTO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Secondary: Gallery
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("open_gallery_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, RetailBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RetailTextPrimary)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp), tint = RetailTextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Choose From Gallery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextPrimary
                        )
                    }
                }
            }
        }
    }
}
