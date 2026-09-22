package top.dingfengbo.mylibrary.ui.books

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.theme.IdentifierTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.EmptyState
import top.dingfengbo.mylibrary.ui.common.rememberHaptics

/**
 * ISBN capture: camera preview with on-device barcode detection, plus a manual entry fallback.
 *
 * The fallback is not decoration — it is the path used when the permission is denied, on a device
 * without a camera, and in the emulator, where the virtual camera cannot produce a real barcode.
 *
 * The preview is one composition: the camera, a scrim that leaves only the frame lit, the frame
 * itself, the hint sitting on its own surface, and the manual field permanently parked below —
 * where a reader whose camera cannot read the code needs it without hunting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsbnScanScreen(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf(false) }

    val haptics = rememberHaptics()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    // Coming back from the system settings, where the reader just allowed the camera: without this
    // the screen keeps showing "denied" until the whole flow is left and re-entered.
    LifecycleResumeEffect(Unit) {
        granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val deliver: (String) -> Unit = { code ->
        if (!delivered) {
            delivered = true
            // The phone is usually held over the book with the other hand, so the read answers with
            // a tick as well as with the form appearing.
            haptics.confirm()
            onScanned(code)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (granted) {
                    BarcodePreview(onBarcode = deliver)
                    ScanOverlay()
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = Spacing.md,
                                vertical = Spacing.sm,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.scan_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else {
                    EmptyState(
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.scan_permission_title),
                        hint = stringResource(R.string.scan_permission_denied),
                        actionLabel = stringResource(R.string.scan_permission_settings),
                        onAction = { context.startActivity(appSettingsIntent(context)) },
                    )
                }
            }

            ManualEntry(
                value = manual,
                onValueChange = { value ->
                    // Digits only, capped at the longest ISBN: the field is numeric, and a paste of
                    // something longer would only ever be rejected.
                    manual = value.filter(Char::isDigit).take(13)
                    manualError = false
                },
                invalid = manualError,
                onConfirm = {
                    if (manual.length == 10 || manual.length == 13) {
                        deliver(manual)
                    } else {
                        manualError = true
                        haptics.reject()
                    }
                },
            )
        }
    }
}

/**
 * The camera image with the frame cut out of it: a scrim above, below and beside the frame, so the
 * reader's eye is left with one bright rectangle to aim with.
 *
 * Both the scrim's hole and the frame come from the same four numbers, drawn in one pass — laid out
 * as separate composables the two drift apart the moment either one changes size.
 */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxSize()) {
        // Clamped so a short preview — landscape, or with the keyboard up — still leaves scrim above
        // and below the frame instead of pushing it off the edge.
        val frameWidth = minOf(size.width * 0.8f, size.height * 0.8f * 1.6f)
        val frameHeight = frameWidth / 1.6f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight

        drawPath(
            path = Path().apply {
                addRect(Rect(0f, 0f, size.width, top))
                addRect(Rect(0f, bottom, size.width, size.height))
                addRect(Rect(0f, top, left, bottom))
                addRect(Rect(right, top, size.width, bottom))
            },
            color = scrim,
        )
        val corners = listOf(
            Offset(left, top) to Offset(right, top),
            Offset(right, top) to Offset(right, bottom),
            Offset(right, bottom) to Offset(left, bottom),
            Offset(left, bottom) to Offset(left, top),
        )
        corners.forEach { (start, end) ->
            drawLine(
                color = accent,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The path for a code the camera cannot read. Always on screen under the preview: on the emulator,
 * with the permission denied, or with a barcode too worn to detect, typing is the only way forward.
 */
@Composable
private fun ManualEntry(
    value: String,
    onValueChange: (String) -> Unit,
    invalid: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // The activity is edge-to-edge and the window is not resized by the keyboard, so the
            // field asks for the IME inset itself to stay visible while it is being typed into.
            .imePadding()
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.scan_manual),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.book_detail_field_isbn)) },
                singleLine = true,
                isError = invalid,
                textStyle = MaterialTheme.typography.bodyLarge.merge(IdentifierTextStyle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Button(onClick = onConfirm) { Text(stringResource(R.string.scan_manual_confirm)) }
        }
        if (invalid) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.scan_manual_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun appSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

@OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodePreview(onBarcode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // The default analysis size is 640x480, which leaves the bars of an ISBN only a hundred
            // pixels wide at reading distance — that is why the same code needed several attempts.
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
        }
    }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                // Only EAN-13 carries an ISBN; UPC-A and EAN-8 would only add codes that never are one.
                .setBarcodeFormats(Barcode.FORMAT_EAN_13)
                .build()
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
            } else {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        isbnFrom(barcodes.mapNotNull { it.rawValue })?.let(onBarcode)
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
        }
        onDispose {
            controller.unbind()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The ISBN out of every barcode detected in one frame, or null when the frame holds none.
 *
 * A book cover carries more than one barcode — the ISBN plus a price or supplementary code — and
 * the detector hands them back in no particular order, so taking whichever came first is how a
 * scan ends up filling the form from the wrong code. Only an EAN-13 in the 978/979 book range is
 * accepted; anything else leaves the camera looking rather than delivering a plausible-looking
 * non-ISBN.
 */
internal fun isbnFrom(codes: List<String>): String? = codes.firstOrNull { code ->
    code.length == 13 && code.all(Char::isDigit) &&
        (code.startsWith("978") || code.startsWith("979"))
}
