package com.example.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AuditPhotoGenerator {
    /**
     * Generates a verified timestamped audit verification photo with embedded metadata,
     * decorative viewfinder grid, official verification stamp, and timestamp banner.
     * Compresses and saves the photo to cache images and returns the local file Uri.
     */
    fun createAuditStampPhoto(
        context: Context,
        taskId: Long,
        taskTitle: String,
        priority: String,
        employeeName: String,
        employeeId: Long,
        timestamp: Long = System.currentTimeMillis()
    ): Uri? {
        return try {
            val width = 720
            val height = 540
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background dark slate
            val bgPaint = Paint().apply {
                color = Color.parseColor("#1C1E22")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Decorative grid pattern
            val gridPaint = Paint().apply {
                color = Color.parseColor("#15FFFFFF")
                strokeWidth = 1f
            }
            for (x in 0..width step 40) {
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
            }
            for (y in 0..height step 40) {
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
            }

            // Outer Frame in Retail Orange
            val framePaint = Paint().apply {
                color = Color.parseColor("#FF6D00")
                style = Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 16f, 16f, framePaint)

            // Viewfinder corner brackets
            val bracketPaint = Paint().apply {
                color = Color.WHITE
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

            // Center target circle
            val centerCirclePaint = Paint().apply {
                color = Color.parseColor("#33FFFFFF")
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawCircle(width / 2f, height / 2f - 20f, 65f, centerCirclePaint)

            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

            // Stamp Header
            val stampHeaderPaint = Paint().apply {
                color = Color.parseColor("#FF6D00")
                textSize = 32f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("★ AUDIT VERIFICATION PROOF ★", width / 2f, 90f, stampHeaderPaint)

            // Task Title
            val taskTitlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 28f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val displayTitle = if (taskTitle.length > 36) taskTitle.take(33) + "..." else taskTitle
            canvas.drawText(displayTitle, width / 2f, 230f, taskTitlePaint)

            // Sub details (Task ID and Priority)
            val subTextPaint = Paint().apply {
                color = Color.parseColor("#B0BEC5")
                textSize = 20f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("TASK REF: #$taskId  •  PRIORITY: ${priority.uppercase()}", width / 2f, 270f, subTextPaint)

            // Employee Sign-off info
            val empTextPaint = Paint().apply {
                color = Color.parseColor("#ECEFF1")
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("VERIFIED BY: $employeeName (ID #$employeeId)", width / 2f, 310f, empTextPaint)

            // Bottom Timestamp banner
            val bannerPaint = Paint().apply {
                color = Color.parseColor("#263238")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(50f, height - 120f, width - 50f, height - 48f), 10f, 10f, bannerPaint)

            val bannerBorderPaint = Paint().apply {
                color = Color.parseColor("#00E676")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(RectF(50f, height - 120f, width - 50f, height - 48f), 10f, 10f, bannerBorderPaint)

            val bannerTextPaint = Paint().apply {
                color = Color.parseColor("#00E676")
                textSize = 22f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("🕒 TIMESTAMP: $timeStr  •  VERIFIED", width / 2f, height - 76f, bannerTextPaint)

            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val photoFile = File(imagesDir, "audit_task_${taskId}_${timestamp}.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Uri.fromFile(photoFile)
        } catch (e: Exception) {
            null
        }
    }
}
