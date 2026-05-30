package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryapreetam.cmpimgcompress.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalEncodingApi::class)
private val MOCK_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

@Composable
fun App() {
  val coroutineScope = rememberCoroutineScope()
  
  // State variables
  var isCompressing by remember { mutableStateOf(false) }
  var compressionError by remember { mutableStateOf<String?>(null) }
  var compressedResult by remember { mutableStateOf<CompressedImage?>(null) }
  
  // Config state
  var useQualityMode by remember { mutableStateOf(true) }
  var qualityInput by remember { mutableStateOf("80") }
  var targetSizeInput by remember { mutableStateOf("50") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
        )
      ),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 500.dp)
        .fillMaxWidth()
        .padding(24.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(Color(0xFF1E293B).copy(alpha = 0.85f))
        .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
        .padding(28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Header
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
          text = "Image Compressor",
          style = TextStyle(
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
          )
        )
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
          text = "Premium Compose Multiplatform Demo",
          style = TextStyle(
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif
          )
        )
      }

      // Divider
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(Color(0xFF334155))
      )

      // Mock Asset Status
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F172A).copy(alpha = 0.5f))
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          BasicText(
            text = "Source Asset",
            style = TextStyle(color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          )
          BasicText(
            text = "Mock 1x1 transparent PNG",
            style = TextStyle(color = Color.White, fontSize = 14.sp)
          )
        }
        BasicText(
          text = "95 B",
          style = TextStyle(color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )
      }

      // Mode Selector
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F172A))
          .padding(4.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (useQualityMode) Color(0xFF334155) else Color.Transparent)
            .clickable { useQualityMode = true }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          BasicText(
            text = "By Quality",
            style = TextStyle(
              color = if (useQualityMode) Color.White else Color(0xFF94A3B8),
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (!useQualityMode) Color(0xFF334155) else Color.Transparent)
            .clickable { useQualityMode = false }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          BasicText(
            text = "By Target Size",
            style = TextStyle(
              color = if (!useQualityMode) Color.White else Color(0xFF94A3B8),
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold
            )
          )
        }
      }

      // Configuration Input fields
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BasicText(
          text = if (useQualityMode) "Quality Percentage (0 - 100)" else "Target Size (KB)",
          style = TextStyle(color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        )
        
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          if (useQualityMode) {
            BasicTextField(
              value = qualityInput,
              onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) qualityInput = it },
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true
            )
          } else {
            BasicTextField(
              value = targetSizeInput,
              onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) targetSizeInput = it },
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true
            )
          }
        }
      }

      // Compress Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(
            brush = Brush.horizontalGradient(
              colors = listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9))
            )
          )
          .clickable(enabled = !isCompressing) {
            isCompressing = true
            compressionError = null
            coroutineScope.launch {
              try {
                val inputBytes = Base64.Mime.decode(MOCK_PNG_BASE64)
                val imageData = ImageData(inputBytes, "image/png")
                
                val config = if (useQualityMode) {
                  val q = qualityInput.toFloatOrNull() ?: 80f
                  CompressionConfig.ByQuality(q)
                } else {
                  val kb = targetSizeInput.toIntOrNull() ?: 50
                  CompressionConfig.ByTargetSize(kb)
                }

                val result = ImageCompressor.compress(imageData, config)
                compressedResult = result
              } catch (e: Exception) {
                compressionError = e.message ?: e.toString()
              } finally {
                isCompressing = false
              }
            }
          }
          .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
      ) {
        BasicText(
          text = if (isCompressing) "Compressing..." else "Run Compression Engine",
          style = TextStyle(
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
          )
        )
      }

      // Error Display
      compressionError?.let { err ->
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          BasicText(
            text = "Error: $err",
            style = TextStyle(color = Color(0xFFFCA5A5), fontSize = 13.sp)
          )
        }
      }

      // Compression Analytics Results Card
      compressedResult?.let { res ->
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.4f))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          BasicText(
            text = "Compression Analytics",
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          )
          
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              BasicText(text = "Output Size", style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp))
              BasicText(text = "${res.compressedSize} B", style = TextStyle(color = Color(0xFF34D399), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
            Column(horizontalAlignment = Alignment.End) {
              BasicText(text = "Format", style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp))
              BasicText(text = res.mimeType.uppercase(), style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
          }

          res.metadata?.let { meta ->
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF334155))
            )
            
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                BasicText(text = "Quality Processed", style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp))
                BasicText(text = "${meta.effectiveQualityPercent ?: 0f}%", style = TextStyle(color = Color.White, fontSize = 13.sp))
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(text = "Iterations", style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp))
                BasicText(text = "${meta.iterations}", style = TextStyle(color = Color.White, fontSize = 13.sp))
              }
              Column(horizontalAlignment = Alignment.End) {
                BasicText(text = "Time Taken", style = TextStyle(color = Color(0xFF64748B), fontSize = 11.sp))
                BasicText(text = "${meta.elapsedMillis} ms", style = TextStyle(color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold))
              }
            }
          }
        }
      }
    }
  }
}