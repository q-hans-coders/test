package io.github.xororz.localdream.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import io.github.xororz.localdream.data.ModelRepository
import io.github.xororz.localdream.service.BackendService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import android.util.Base64 as AndroidBase64
import java.util.Scanner
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.GenerationPreferences
import io.github.xororz.localdream.service.BackgroundGenerationService
import io.github.xororz.localdream.service.BackgroundGenerationService.GenerationState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.geometry.Offset
import io.github.xororz.localdream.BuildConfig

import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.graphics.BitmapFactory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import kotlinx.coroutines.CoroutineScope

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResult

import org.json.JSONArray
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import io.github.xororz.localdream.ArViewActivity
import io.github.xororz.localdream.service.MeshyService
import io.github.xororz.localdream.service.MeshyModelUrls
import android.util.Log
import androidx.compose.runtime.Composable

private val openAiClient by lazy {
    OkHttpClient.Builder().build()
}
private val meshyClient by lazy {
    OkHttpClient.Builder().build()
}

private suspend fun translateToEnglish(text: String): String = withContext(Dispatchers.IO) {

    val payload = JSONObject().apply {
        put("model", "gpt-3.5-turbo")
        put("messages", JSONArray().apply {
            // 변경된 시스템 메시지
            put(JSONObject().apply {
                put("role", "system")
                put("content",
                    "You are a translator. " +
                            "If the user’s text is not in English, translate it to English. " +
                            "If it is already English, return it unchanged."
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", text)
            })
        })
    }
    val req = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    openAiClient.newCall(req).execute().use { res ->
        if (!res.isSuccessful) throw IOException("OpenAI Error: ${res.code}")
        JSONObject(res.body!!.string())
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}



private suspend fun reportImage(
    context: Context,
    bitmap: Bitmap,
    modelName: String,
    params: GenerationParameters,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            val base64Image = AndroidBase64.encodeToString(
                byteArray,
                AndroidBase64.NO_WRAP
            )

            val jsonObject = JSONObject().apply {
                put("model_name", modelName)
                put("generation_params", JSONObject().apply {
                    put("prompt", params.prompt)
                    put("negative_prompt", params.negativePrompt)
                    put("steps", params.steps)
                    put("cfg", params.cfg)
                    put("seed", params.seed ?: JSONObject.NULL)
                    put("size", params.size)
                    put("run_on_cpu", params.runOnCpu)
                    put("generation_time", params.generationTime ?: JSONObject.NULL)
                })
                put("image_data", base64Image)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val requestBody = jsonObject.toString()
                .toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url("https://report.chino.icu/report")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    onError("Report failed: ${response.code}")
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
//                onError("Failed to report: ${e.localizedMessage}")
                onError("Network Error")
            }
        }
    }
}

private suspend fun saveImage(
    context: Context,
    bitmap: Bitmap,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "generated_image_$timestamp.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val resolver = context.contentResolver
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw IOException("Failed to create MediaStore entry")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                } ?: throw IOException("Failed to open output stream")
            } else {
                // Android 9
                val imagesDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                    ),
                    "LocalDream"
                )

                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }

                val file = File(imagesDir, filename)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.toString()),
                    arrayOf("image/png"),
                    null
                )
            }

            withContext(Dispatchers.Main) {
                onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to save: ${e.localizedMessage}")
            }
        }
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        true // Android 10
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private suspend fun checkBackendHealth(
    onHealthy: () -> Unit,
    onUnhealthy: () -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)  // 100ms
            .build()

        val startTime = System.currentTimeMillis()
//        val timeoutDuration = 10000
        val timeoutDuration = 60000

        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() - startTime > timeoutDuration) {
                withContext(Dispatchers.Main) {
                    onUnhealthy()
                }
                break
            }

            try {
                val request = Request.Builder()
                    .url("http://localhost:8081/health")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        onHealthy()
                    }
                    break
                }
            } catch (e: Exception) {
                // e
            }

            delay(100)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onUnhealthy()
        }
    }
}

data class GenerationParameters(
    val steps: Int,
    val cfg: Float,
    val seed: Long?,
    val prompt: String,
    val negativePrompt: String,
    val generationTime: String?,
    val size: Int,
    val runOnCpu: Boolean,
    val denoiseStrength: Float = 0.6f,
    val inputImage: String? = null
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelRunScreen(
    modelId: String,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val backendState by BackendService.backendState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generationPreferences = remember { GenerationPreferences(context) }
    var isConverting3D by remember { mutableStateOf(false) }
    var meshUrls by remember { mutableStateOf<MeshyModelUrls?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelRepository = remember { ModelRepository(context) }
    val model = remember { modelRepository.models.find { it.id == modelId } }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageVersion by remember { mutableStateOf(0) }
    var generationParams by remember { mutableStateOf<GenerationParameters?>(null) }
    var generationParamsTmp by remember {
        mutableStateOf(
            GenerationParameters(
                steps = 0,
                cfg = 0f,
                seed = 0,
                prompt = "",
                negativePrompt = "",
                generationTime = "",
                size = if (model?.runOnCpu == true) 256 else model?.generationSize ?: 512,
                runOnCpu = model?.runOnCpu ?: false
            )
        )
    }
    var prompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("") }
    var cfg by remember { mutableStateOf(7f) }
    var steps by remember { mutableStateOf(20f) }
    var seed by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(if (model?.runOnCpu == true) 256 else 512) }
    var denoiseStrength by remember { mutableStateOf(0.6f) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var base64EncodeDone by remember { mutableStateOf(false) }
    var returnedSeed by remember { mutableStateOf<Long?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBackendReady by remember { mutableStateOf(false) }
    var isCheckingBackend by remember { mutableStateOf(true) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showParametersDialog by remember { mutableStateOf(false) }
    var pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var generationTime by remember { mutableStateOf<String?>(null) }
    var generationStartTime by remember { mutableStateOf<Long?>(null) }
    var hasInitialized by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    var isPreviewMode by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val useImg2img = preferences.getBoolean("use_img2img", true)

    var showCropScreen by remember { mutableStateOf(false) }
    var imageUriForCrop by remember { mutableStateOf<Uri?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showInpaintScreen by remember { mutableStateOf(false) }
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isInpaintMode by remember { mutableStateOf(false) }
    var savedPathHistory by remember { mutableStateOf<List<PathData>?>(null) }
    var isNegativeSpeech by remember { mutableStateOf(false) }
    // RECORD_AUDIO 권한 요청 런처
    val requestRecordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "음성 입력 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (isNegativeSpeech) {
                negativePrompt += " $spoken"
                scope.launch {
                    generationPreferences.saveNegativePrompt(modelId, negativePrompt)
                }
            } else {
                prompt += " $spoken"
                scope.launch {
                    generationPreferences.savePrompt(modelId, prompt)
                }
            }
        }
    }

    fun startSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "음성으로 입력하세요")
            }
            speechLauncher.launch(intent)
        }
    }

    fun processSelectedImage(uri: Uri) {
        imageUriForCrop = uri
        showCropScreen = true
    }

    fun handleCropComplete(base64String: String, bitmap: Bitmap) {
        showCropScreen = false
        selectedImageUri = imageUriForCrop
        imageUriForCrop = null
        croppedBitmap = bitmap

        scope.launch(Dispatchers.IO) {
            try {
                base64EncodeDone = false
                val tmpFile = File(context.filesDir, "tmp.txt")
                tmpFile.writeText(base64String)
                base64EncodeDone = true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    selectedImageUri = null
                    croppedBitmap = null
                }
            }
        }
    }

    fun handleInpaintComplete(
        maskBase64: String,
        originalBitmap: Bitmap,
        maskBmp: Bitmap,
        pathHistory: List<PathData>
    ) {
        showInpaintScreen = false
        isInpaintMode = true
        maskBitmap = maskBmp
        savedPathHistory = pathHistory

        scope.launch(Dispatchers.IO) {
            try {
                val tmpFile = File(context.filesDir, "tmp.txt")
                val originalBase64 = tmpFile.readText()

                val maskFile = File(context.filesDir, "mask.txt")
                maskFile.writeText(maskBase64)

                withContext(Dispatchers.Main) {
                    base64EncodeDone = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    isInpaintMode = false
                    maskBitmap = null
                    savedPathHistory = null
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    val contentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processSelectedImage(it) }
    }

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }


    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {

            cameraImageUri?.let { processSelectedImage(it) }
        } else {
            cameraImageUri = null
        }
    }

    val requestMediaImagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contentPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.media_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contentPickerLauncher.launch("image/*")
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.media_permission_hint),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onSelectImageClick() {
        when {
            // Android 13+
            Build.VERSION.SDK_INT >= 33 -> {
                // PhotoPicker API
                photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            }

            // Android 12-
            else -> {
                when {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        contentPickerLauncher.launch("image/*")
                    }

                    else -> {
                        requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }

    fun handleSaveImage(
        context: Context,
        bitmap: Bitmap,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!checkStoragePermission(context)) {
            onError("need storage permission to save image")
            return
        }

        coroutineScope.launch {
            saveImage(
                context = context,
                bitmap = bitmap,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    class BitmapState(var bitmap: Bitmap? = null) {
        fun clear() {
            try {
                if (bitmap?.isRecycled == false) {
                    bitmap?.recycle()
                }
                bitmap = null
            } catch (e: Exception) {
                android.util.Log.e("BitmapState", "clear Bitmap error", e)
            }
        }
    }

    val bitmapState = remember { BitmapState() }

    fun cleanup() {
        try {
            currentBitmap?.recycle()
            currentBitmap = null
            generationParams = null
            bitmapState.clear()
            context.sendBroadcast(Intent(BackgroundGenerationService.ACTION_STOP))
            val backendServiceIntent = Intent(context, BackendService::class.java)
            context.stopService(backendServiceIntent)
            isRunning = false
            progress = 0f
            errorMessage = null
            BackgroundGenerationService.resetState()
            coroutineScope.launch {
                pagerState.scrollToPage(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("ModelRunScreen", "error", e)
        }
    }

    fun handleExit() {
        cleanup()
        BackgroundGenerationService.clearCompleteState()
        navController.navigateUp()
    }

    LaunchedEffect(modelId) {
        if (!hasInitialized) {
            generationPreferences.getPreferences(modelId).collect { prefs ->
                if (!hasInitialized && prefs.prompt.isEmpty() && prefs.negativePrompt.isEmpty()) {
                    model?.let { m ->
                        if (m.defaultPrompt.isNotEmpty()) {
                            generationPreferences.savePrompt(modelId, m.defaultPrompt)
                        }
                        if (m.defaultNegativePrompt.isNotEmpty()) {
                            generationPreferences.saveNegativePrompt(
                                modelId,
                                m.defaultNegativePrompt
                            )
                        }
                    }
                }
                hasInitialized = true
            }
        }
    }
    LaunchedEffect(modelId) {
        generationPreferences.getPreferences(modelId).collect { prefs ->
            prompt = prefs.prompt
            negativePrompt = prefs.negativePrompt
            steps = prefs.steps
            cfg = prefs.cfg
            seed = prefs.seed
            size = if (model?.runOnCpu == true) prefs.size else model?.generationSize ?: 512
            denoiseStrength = prefs.denoiseStrength
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            cleanup()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    bitmapState.clear()
                }

                Lifecycle.Event.ON_START -> {
                    if (backendState !is BackendService.BackendState.Running) {
                        val intent = Intent(context, BackendService::class.java).apply {
                            putExtra("modelId", model?.id)
                        }
                        context.startForegroundService(intent)
                    }
                }

                Lifecycle.Event.ON_DESTROY -> {
                    cleanup()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cleanup()
        }
    }

    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is GenerationState.Progress -> {
                if (progress == 0f) {
                    generationStartTime = System.currentTimeMillis()
                }
                progress = state.progress
                isRunning = true
            }

            is GenerationState.Complete -> {
                withContext(Dispatchers.Main) {
                    android.util.Log.d("ModelRunScreen", "update bitmap")

                    currentBitmap?.recycle()
                    currentBitmap = state.bitmap
                    imageVersion += 1

                    state.seed?.let { returnedSeed = it }
                    isRunning = false
                    progress = 0f

                    val genTime = generationStartTime?.let { startTime ->
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime
                        when {
                            duration < 1000 -> "${duration}ms"
                            duration < 60000 -> String.format("%.1fs", duration / 1000.0)
                            else -> String.format(
                                "%dm%ds",
                                duration / 60000,
                                (duration % 60000) / 1000
                            )
                        }
                    }

                    generationParams = GenerationParameters(
                        steps = generationParamsTmp.steps,
                        cfg = generationParamsTmp.cfg,
                        seed = returnedSeed,
                        prompt = generationParamsTmp.prompt,
                        negativePrompt = generationParamsTmp.negativePrompt,
                        generationTime = genTime,
                        size = if (model?.runOnCpu == true) generationParamsTmp.size else model?.generationSize
                            ?: 512,
                        runOnCpu = model?.runOnCpu ?: false
                    )

                    android.util.Log.d(
                        "ModelRunScreen",
                        "params update: ${generationParams?.steps}, ${generationParams?.cfg}"
                    )

                    generationTime = genTime
                    generationStartTime = null

                    if (pagerState.currentPage != 1) {
                        pagerState.animateScrollToPage(1)
                    }
                }
            }

            is GenerationState.Error -> {
                errorMessage = state.message
                isRunning = false
                progress = 0f
            }

            else -> {
                isRunning = false
                progress = 0f
            }
        }
    }

    BackHandler {
        if (isRunning) {
            showExitDialog = true
        } else {
            handleExit()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.confirm_exit)) },
            text = { Text(stringResource(R.string.confirm_exit_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        handleExit()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(stringResource(R.string.reset)) },
            text = { Text(stringResource(R.string.reset_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            generationPreferences.saveSteps(modelId, 20f)
                            generationPreferences.saveCfg(modelId, 7f)
                            generationPreferences.saveSeed(modelId, "")
                            generationPreferences.savePrompt(modelId, model?.defaultPrompt ?: "")
                            generationPreferences.saveNegativePrompt(
                                modelId,
                                model?.defaultNegativePrompt ?: ""
                            )
                            generationPreferences.saveSize(modelId, 256)
                            generationPreferences.saveDenoiseStrength(modelId, 0.6f)

                            withContext(Dispatchers.Main) {
                                steps = 20f
                                cfg = 7f
                                seed = ""
                                prompt = model?.defaultPrompt ?: ""
                                negativePrompt = model?.defaultNegativePrompt ?: ""
                            }
                        }
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        checkBackendHealth(
            onHealthy = {
                isBackendReady = true
                isCheckingBackend = false
            },
            onUnhealthy = {
                isBackendReady = false
                isCheckingBackend = false
                errorMessage = context.getString(R.string.backend_failed)
            }
        )
    }
    fun canDisplayBitmap(): Boolean {
        return bitmapState.bitmap != null && !bitmapState.bitmap!!.isRecycled
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(model?.name ?: "Running Model")
                            Text(
                                model?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    /*navigationIcon = {
                        IconButton(onClick = {
                            if (isRunning) {
                                showExitDialog = true
                            } else {
                                handleExit()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },*/
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Row {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        focusManager.clearFocus()
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (pagerState.currentPage == 0)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(stringResource(R.string.prompt_tab))
                            }
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        focusManager.clearFocus()
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (pagerState.currentPage == 1)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            ) {
                                Text(stringResource(R.string.result_tab))
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (model != null) {

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) { page ->
                    when (page) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                stringResource(R.string.prompt_settings),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            var showAdvancedSettings by remember {
                                                mutableStateOf(
                                                    false
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 화면 상단의 img2img 버튼 위치에 이 코드를 넣으세요
                                                if (useImg2img) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        // 앨범
                                                        TextButton(onClick = { onSelectImageClick() }) {
                                                            Icon(
                                                                Icons.Default.Image,
                                                                contentDescription = "앨범에서 선택",
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("앨범")
                                                        }
                                                        // 카메라
                                                        TextButton(onClick = {
                                                            // cache 에 임시 파일 만들고 URI 얻기
                                                            val photoFile = File(
                                                                context.cacheDir,
                                                                "camera_${System.currentTimeMillis()}.jpg"
                                                            )
                                                            cameraImageUri =
                                                                FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    photoFile
                                                                )
                                                            // launcher 실행 (URI 는 절대 null 이 아니어야 합니다)
                                                            cameraLauncher.launch(cameraImageUri!!)
                                                        }) {
                                                            Icon(
                                                                Icons.Default.CameraAlt,
                                                                contentDescription = "카메라로 촬영",
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("카메라")
                                                        }
                                                    }
                                                }

                                                TextButton(
                                                    onClick = { showAdvancedSettings = true }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Settings,
                                                        contentDescription = stringResource(R.string.settings),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(
                                                        stringResource(R.string.advanced_settings),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )

                                                }
                                            }
                                            if (showAdvancedSettings) {
                                                AlertDialog(
                                                    onDismissRequest = {
                                                        showAdvancedSettings = false
                                                    },
                                                    title = { Text(stringResource(R.string.advanced_settings_title)) },
                                                    text = {
                                                        Column(
                                                            verticalArrangement = Arrangement.spacedBy(
                                                                16.dp
                                                            ),
                                                            modifier = Modifier.padding(vertical = 8.dp)
                                                        ) {
                                                            Column {
                                                                Text(
                                                                    stringResource(
                                                                        R.string.steps,
                                                                        steps.roundToInt()
                                                                    ),
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                                Slider(
                                                                    value = steps,
                                                                    onValueChange = {
                                                                        steps = it

                                                                        scope.launch {
                                                                            generationPreferences.saveSteps(
                                                                                modelId,
                                                                                steps
                                                                            )
                                                                        }
                                                                    },
                                                                    valueRange = 1f..50f,
                                                                    steps = 48,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }

                                                            Column {
                                                                Text(
                                                                    "CFG Scale: %.1f".format(cfg),
                                                                    style = MaterialTheme.typography.bodyMedium
                                                                )
                                                                Slider(
                                                                    value = cfg,
                                                                    onValueChange = {
                                                                        cfg = it

                                                                        scope.launch {
                                                                            generationPreferences.saveCfg(
                                                                                modelId,
                                                                                cfg
                                                                            )
                                                                        }
                                                                    },
                                                                    valueRange = 1f..30f,
                                                                    steps = 57,
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }
                                                            if (model.runOnCpu) {
                                                                Column {
                                                                    Text(
                                                                        stringResource(
                                                                            R.string.image_size,
                                                                            size,
                                                                            size
                                                                        ),
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    Column(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalArrangement = Arrangement.spacedBy(
                                                                            4.dp
                                                                        )
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                                8.dp
                                                                            )
                                                                        ) {
                                                                            listOf(
                                                                                128,
                                                                                256
                                                                            ).forEach { sizeOption ->
                                                                                FilterChip(
                                                                                    selected = size == sizeOption,
                                                                                    onClick = {
                                                                                        size =
                                                                                            sizeOption
                                                                                        scope.launch {
                                                                                            generationPreferences.saveSize(
                                                                                                modelId,
                                                                                                size
                                                                                            )
                                                                                        }
                                                                                    },
                                                                                    label = { Text("${sizeOption}px") },
                                                                                    modifier = Modifier.weight(
                                                                                        1f
                                                                                    )
                                                                                )
                                                                            }
                                                                        }
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                                8.dp
                                                                            )
                                                                        ) {
                                                                            listOf(
                                                                                384,
                                                                                512
                                                                            ).forEach { sizeOption ->
                                                                                FilterChip(
                                                                                    selected = size == sizeOption,
                                                                                    onClick = {
                                                                                        size =
                                                                                            sizeOption
                                                                                        scope.launch {
                                                                                            generationPreferences.saveSize(
                                                                                                modelId,
                                                                                                size
                                                                                            )
                                                                                        }
                                                                                    },
                                                                                    label = { Text("${sizeOption}px") },
                                                                                    modifier = Modifier.weight(
                                                                                        1f
                                                                                    )
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            if (useImg2img) {
                                                                Column {
                                                                    Text(
                                                                        "(img2img)Denoise Strength: %.2f".format(
                                                                            denoiseStrength
                                                                        ),
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                    )
                                                                    Slider(
                                                                        value = denoiseStrength,
                                                                        onValueChange = {
                                                                            denoiseStrength = it
                                                                            scope.launch {
                                                                                generationPreferences.saveDenoiseStrength(
                                                                                    modelId,
                                                                                    denoiseStrength
                                                                                )
                                                                            }
                                                                        },
                                                                        valueRange = 0f..1f,
                                                                        steps = 99,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    )
                                                                }
                                                            }
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(
                                                                    8.dp
                                                                )
                                                            ) {
                                                                OutlinedTextField(
                                                                    value = seed,
                                                                    onValueChange = {
                                                                        seed = it
                                                                        scope.launch {
                                                                            generationPreferences.saveSeed(
                                                                                modelId,
                                                                                seed
                                                                            )
                                                                        }
                                                                    },
                                                                    label = { Text(stringResource(R.string.random_seed)) },
                                                                    keyboardOptions = KeyboardOptions(
                                                                        keyboardType = KeyboardType.Number
                                                                    ),
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = MaterialTheme.shapes.medium,
                                                                    trailingIcon = {
                                                                        if (seed.isNotEmpty()) {
                                                                            IconButton(onClick = {
                                                                                seed = ""
                                                                                scope.launch {
                                                                                    generationPreferences.saveSeed(
                                                                                        modelId,
                                                                                        seed
                                                                                    )
                                                                                }

                                                                            }) {
                                                                                Icon(
                                                                                    Icons.Default.Clear,
                                                                                    contentDescription = "clear"
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                )

                                                                if (returnedSeed != null) {
                                                                    FilledTonalButton(
                                                                        onClick = {
                                                                            seed =
                                                                                returnedSeed.toString()
                                                                            scope.launch {
                                                                                generationPreferences.saveSeed(
                                                                                    modelId,
                                                                                    seed
                                                                                )
                                                                            }
                                                                        },
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Refresh,
                                                                            contentDescription = stringResource(
                                                                                R.string.use_last_seed
                                                                            ),
                                                                            modifier = Modifier
                                                                                .size(
                                                                                    20.dp
                                                                                )
                                                                                .padding(end = 4.dp)
                                                                        )
                                                                        Text(
                                                                            stringResource(
                                                                                R.string.use_last_seed,
                                                                                returnedSeed.toString()
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    confirmButton = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    showResetConfirmDialog = true
                                                                },
                                                                colors = ButtonDefaults.textButtonColors(
                                                                    contentColor = MaterialTheme.colorScheme.error
                                                                )
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Refresh,
                                                                    contentDescription = stringResource(
                                                                        R.string.reset
                                                                    ),
                                                                    modifier = Modifier
                                                                        .size(20.dp)
                                                                        .padding(end = 4.dp)
                                                                )
                                                                Text(stringResource(R.string.reset))
                                                            }

                                                            TextButton(onClick = {
                                                                showAdvancedSettings = false
                                                            }) {
                                                                Text(stringResource(R.string.confirm))
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }

                                        var expandedPrompt by remember { mutableStateOf(false) }
                                        var expandedNegativePrompt by remember {
                                            mutableStateOf(
                                                false
                                            )
                                        }

                                        OutlinedTextField(
                                            value = prompt,
                                            onValueChange = {
                                                prompt = it

                                                scope.launch {
                                                    generationPreferences.savePrompt(
                                                        modelId,
                                                        prompt
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) { },
                                            label = { Text(stringResource(R.string.image_prompt)) },
                                            maxLines = if (expandedPrompt) Int.MAX_VALUE else 2,
                                            minLines = if (expandedPrompt) 3 else 2,
                                            shape = MaterialTheme.shapes.medium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            trailingIcon = {
                                                Row {
                                                    // 음성 버튼
                                                    IconButton(onClick = {
                                                        isNegativeSpeech = false
                                                        startSpeechRecognition()
                                                    }) {
                                                        Icon(
                                                            Icons.Default.Mic,
                                                            contentDescription = "음성 입력"
                                                        )
                                                    }
                                                    // 원래 있던 펼침/접기 버튼
                                                    IconButton(onClick = {
                                                        expandedPrompt = !expandedPrompt
                                                    }) {
                                                        Icon(
                                                            if (expandedPrompt) Icons.Default.KeyboardArrowUp
                                                            else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = if (expandedPrompt) "접기" else "펼치기"
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        OutlinedTextField(
                                            value = negativePrompt,
                                            onValueChange = {
                                                negativePrompt = it
                                                scope.launch {
                                                    generationPreferences.saveNegativePrompt(
                                                        modelId,
                                                        negativePrompt
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) { },
                                            label = { Text(stringResource(R.string.negative_prompt)) },
                                            maxLines = if (expandedNegativePrompt) Int.MAX_VALUE else 2,
                                            minLines = if (expandedNegativePrompt) 3 else 2,
                                            shape = MaterialTheme.shapes.medium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            trailingIcon = {
                                                Row {
                                                    IconButton(onClick = {
                                                        isNegativeSpeech = true
                                                        startSpeechRecognition()
                                                    }) {
                                                        Icon(
                                                            Icons.Default.Mic,
                                                            contentDescription = "음성 입력"
                                                        )
                                                    }
                                                    IconButton(onClick = {
                                                        expandedNegativePrompt =
                                                            !expandedNegativePrompt
                                                    }) {
                                                        Icon(
                                                            if (expandedNegativePrompt) Icons.Default.KeyboardArrowUp
                                                            else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = if (expandedNegativePrompt) "접기" else "펼치기"
                                                        )
                                                    }
                                                }
                                            }
                                        )

                                        Button(
                                            onClick = {
                                                focusManager.clearFocus()
                                                coroutineScope.launch {
                                                    // 한글 → 영어 번역 (빈 값은 번역 호출 안 함)
                                                    val enPrompt = if (prompt.isBlank()) {
                                                        ""
                                                    } else {
                                                        translateToEnglish(prompt)
                                                    }

                                                    val enNegPrompt =
                                                        if (negativePrompt.isBlank()) {
                                                            ""
                                                        } else {
                                                            translateToEnglish(negativePrompt)
                                                        }

                                                    // generationParamsTmp 설정
                                                    generationParamsTmp = GenerationParameters(
                                                        steps = steps.roundToInt(),
                                                        cfg = cfg,
                                                        seed = 0,
                                                        prompt = enPrompt,
                                                        negativePrompt = enNegPrompt,
                                                        generationTime = "",
                                                        size = size,
                                                        runOnCpu = model.runOnCpu,
                                                        denoiseStrength = denoiseStrength,
                                                        inputImage = null
                                                    )

                                                    // 서비스 호출 Intent 구성
                                                    val intent = Intent(
                                                        context,
                                                        BackgroundGenerationService::class.java
                                                    ).apply {
                                                        putExtra("prompt", enPrompt)
                                                        putExtra("negative_prompt", enNegPrompt)
                                                        putExtra("steps", steps.roundToInt())
                                                        putExtra("cfg", cfg)
                                                        seed.toLongOrNull()
                                                            ?.let { putExtra("seed", it) }
                                                        putExtra("size", size)
                                                        putExtra(
                                                            "denoise_strength",
                                                            denoiseStrength
                                                        )
                                                        if (selectedImageUri != null && base64EncodeDone) {
                                                            putExtra("has_image", true)
                                                            if (isInpaintMode && maskBitmap != null) {
                                                                putExtra("has_mask", true)
                                                            }
                                                        }
                                                    }

                                                    context.startForegroundService(intent)
                                                }
                                            },
                                            enabled = serviceState !is GenerationState.Progress,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            if (serviceState is GenerationState.Progress) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(
                                                        24.dp
                                                    )
                                                )
                                            } else {
                                                Text(stringResource(R.string.generate_image))
                                            }
                                        }
                                    }
                                }


                                AnimatedVisibility(
                                    visible = errorMessage != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    errorMessage?.let { msg ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Error,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    msg,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = isRunning,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.generating),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(
                                                "${(progress * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = selectedImageUri != null && base64EncodeDone,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start
                                        ) {
                                            Card(
                                                modifier = Modifier
                                                    .size(100.dp),
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Box {
                                                    if (croppedBitmap != null) {
                                                        Image(
                                                            bitmap = croppedBitmap!!.asImageBitmap(),
                                                            contentDescription = "Cropped Image",
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        selectedImageUri?.let { uri ->
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(
                                                                    LocalContext.current
                                                                )
                                                                    .data(uri)
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = "Selected Image",
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            selectedImageUri = null
                                                            croppedBitmap = null
                                                            maskBitmap = null
                                                            isInpaintMode = false
                                                        },
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(
                                                                color = MaterialTheme.colorScheme.surface.copy(
                                                                    alpha = 0.7f
                                                                ),
                                                                shape = CircleShape
                                                            )
                                                            .align(Alignment.TopEnd)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Clear,
                                                            contentDescription = "Remove Image",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            AnimatedVisibility(
                                                visible = croppedBitmap != null && !isInpaintMode,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Row {
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    FilledTonalIconButton(
                                                        onClick = {
                                                            if (croppedBitmap != null) {
                                                                showInpaintScreen = true
                                                            } else {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Please Crop First",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        },
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Brush,
                                                            contentDescription = "Set Mask",
                                                        )
                                                    }
                                                }
                                            }

                                            AnimatedVisibility(
                                                visible = isInpaintMode && maskBitmap != null,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally()
                                            ) {
                                                Row {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Card(
                                                        modifier = Modifier
                                                            .size(100.dp)
                                                            .clickable {
                                                                if (croppedBitmap != null && maskBitmap != null) {
                                                                    showInpaintScreen = true
                                                                }
                                                            },
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Box {
                                                            maskBitmap?.let { mb ->
                                                                Image(
                                                                    bitmap = mb.asImageBitmap(),
                                                                    contentDescription = "Mask Image",
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    maskBitmap = null
                                                                    isInpaintMode = false
                                                                    savedPathHistory = null
                                                                },
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .background(
                                                                        color = MaterialTheme.colorScheme.surface.copy(
                                                                            alpha = 0.7f
                                                                        ),
                                                                        shape = CircleShape
                                                                    )
                                                                    .align(Alignment.TopEnd)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Clear,
                                                                    contentDescription = "Clear Mask",
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = currentBitmap == null,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                stringResource(R.string.no_results),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                stringResource(R.string.no_results_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(0)
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 8.dp)
                                            ) {
                                                Text(stringResource(R.string.go_to_generate))
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = currentBitmap != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    stringResource(R.string.result_tab),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                currentBitmap?.let { bitmap ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                    ) {
                                                        if (BuildConfig.FLAVOR == "filter") {
                                                            FilledTonalIconButton(
                                                                onClick = {
                                                                    showReportDialog = true
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Report,
                                                                    contentDescription = "report inappropriate content"
                                                                )
                                                            }
                                                        }

                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                handleSaveImage(
                                                                    context = context,
                                                                    bitmap = bitmap,
                                                                    onSuccess = {
                                                                        Toast.makeText(
                                                                            context,
                                                                            context.getString(R.string.image_saved),
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    },
                                                                    onError = { error ->
                                                                        Toast.makeText(
                                                                            context,
                                                                            error,
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                                )
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Save,
                                                                contentDescription = "save image"
                                                            )
                                                        }
                                                        FilledTonalIconButton(
                                                            onClick = {
                                                                // 1) 변환 중이거나 이미지가 없으면 무시
                                                                if (isConverting3D || currentBitmap == null) return@FilledTonalIconButton

                                                                // 2) 3D 변환 시작
                                                                isConverting3D = true
                                                                coroutineScope.launch {
                                                                    try {
                                                                        // IO 스레드에서 작업 실행
                                                                        val taskId = withContext(Dispatchers.IO) {
                                                                            MeshyService.createTask(currentBitmap!!)
                                                                        }
                                                                        val urls = withContext(Dispatchers.IO) {
                                                                            MeshyService.pollTask(taskId)
                                                                        }
                                                                        // 성공적으로 URL을 받으면 AR 화면으로 넘길 준비
                                                                        meshUrls = urls
                                                                    } catch (e: Exception) {
                                                                        Log.e("ModelRunScreen", "Meshy 3D 변환 에러", e)
                                                                        Toast.makeText(context, "3D 변환 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                    } finally {
                                                                        // 작업 완료 플래그 해제
                                                                        isConverting3D = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            if (isConverting3D) {
                                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                            } else {
                                                                Icon(imageVector = Icons.Default.Add, contentDescription = "view in AR")
                                                            }
                                                        }

                                                    }
                                                }
                                            }

                                            key(imageVersion) {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .clickable {
                                                            if (currentBitmap != null && !currentBitmap!!.isRecycled) {
                                                                isPreviewMode = true
                                                                scale = 1f
                                                                offsetX = 0f
                                                                offsetY = 0f
                                                            }
                                                        },
                                                    shape = MaterialTheme.shapes.medium,
                                                    shadowElevation = 4.dp
                                                ) {
                                                    currentBitmap?.let { bitmap ->
                                                        if (!bitmap.isRecycled) {
                                                            Image(
                                                                bitmap = bitmap.asImageBitmap(),
                                                                contentDescription = "generated image",
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showParametersDialog = true },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            stringResource(R.string.generation_params),
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Icon(
                                                            Icons.Default.Info,
                                                            contentDescription = "view details",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    generationParams?.let { params ->
                                                        Text(
                                                            stringResource(
                                                                R.string.result_params,
                                                                params.steps,
                                                                params.cfg,
                                                                params.seed.toString()
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.8f
                                                            )
                                                        )
                                                        Text(
                                                            stringResource(
                                                                R.string.result_params_2,
                                                                params.size,
                                                                params.generationTime ?: "unknown",
                                                                if (params.runOnCpu) "CPU" else "NPU"
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.8f
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (showReportDialog && currentBitmap != null && generationParams != null) {
                                    AlertDialog(
                                        onDismissRequest = { showReportDialog = false },
                                        title = { Text("Report") },
                                        text = {
                                            Column {
//                                                Text("Report this image?")
                                                Text(
                                                    "Report this image if you feel it is inappropriate. Params and image will be sent to the server for review.",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    showReportDialog = false
                                                    coroutineScope.launch {
                                                        currentBitmap?.let { bitmap ->
                                                            reportImage(
                                                                context = context,
                                                                bitmap = bitmap,
                                                                modelName = model.name,
                                                                params = generationParams!!,
                                                                onSuccess = {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Thanks for your report.",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                },
                                                                onError = { error ->
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Error: $error",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Report")
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showReportDialog = false }) {
                                                Text(stringResource(R.string.cancel))
                                            }
                                        }
                                    )
                                }
                                if (showParametersDialog && generationParams != null) {
                                    AlertDialog(
                                        onDismissRequest = { showParametersDialog = false },
                                        title = { Text(stringResource(R.string.params_detail)) },
                                        text = {
                                            Column(
                                                modifier = Modifier
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        stringResource(R.string.basic_params),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_step,
                                                            generationParams?.steps ?: 0
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        "CFG: %.1f".format(generationParams?.cfg),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_size,
                                                            generationParams?.size ?: 0,
                                                            generationParams?.size ?: 0
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    generationParams?.seed?.let {
                                                        Text(
                                                            stringResource(R.string.basic_seed, it),
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_runtime,
                                                            if (generationParams?.runOnCpu == true) "CPU" else "NPU"
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        stringResource(
                                                            R.string.basic_time,
                                                            generationParams?.generationTime
                                                                ?: "unknown"
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        stringResource(R.string.image_prompt),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        generationParams?.prompt ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        stringResource(R.string.negative_prompt),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        generationParams?.negativePrompt ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showParametersDialog = false }) {
                                                Text(stringResource(R.string.close))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(meshUrls) {
            meshUrls?.let { urls ->
                Intent(context, ArViewActivity::class.java)
                    .apply { putExtra("model_url", urls.glb) }
                    .also(context::startActivity)
                meshUrls = null // 재진입 방지
                isConverting3D = false
            }
        }

        // ② (선택) 3D 변환 중 오버레이 로딩 화면
        if (isConverting3D) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading_model))
            }
        }
        if (showCropScreen && imageUriForCrop != null) {
            CropImageScreen(
                imageUri = imageUriForCrop!!,
                onCropComplete = { base64String, bitmap ->
                    handleCropComplete(base64String, bitmap)
                },
                onCancel = {
                    showCropScreen = false
                    imageUriForCrop = null
                    selectedImageUri = null
                }
            )
        }
        if (showInpaintScreen && croppedBitmap != null) {
            InpaintScreen(
                originalBitmap = croppedBitmap!!,
                existingMaskBitmap = if (isInpaintMode) maskBitmap else null,
                existingPathHistory = savedPathHistory,
                onInpaintComplete = { maskBase64, originalBitmap, maskBitmap, pathHistory ->
                    handleInpaintComplete(maskBase64, originalBitmap, maskBitmap, pathHistory)
                },
                onCancel = {
                    showInpaintScreen = false
                }
            )
        }
    }
    if (isPreviewMode && currentBitmap != null && !currentBitmap!!.isRecycled) {
        BackHandler {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            isPreviewMode = false
        }

        val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(0.5f, 5f)

            offsetX += offsetChange.x
            offsetY += offsetChange.y
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    isPreviewMode = false
                }
        ) {
            Image(
                bitmap = currentBitmap!!.asImageBitmap(),
                contentDescription = "preview image",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .align(Alignment.Center)
                    .transformable(state = transformableState)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        isPreviewMode = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "close preview",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "reset zoom",
                    tint = Color.White
                )
            }

            Text(
                text = "${(scale * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    AnimatedVisibility(
        visible = isCheckingBackend,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.loading_model),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
