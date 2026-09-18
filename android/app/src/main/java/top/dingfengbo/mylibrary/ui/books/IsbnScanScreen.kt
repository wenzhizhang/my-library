package top.dingfengbo.mylibrary.ui.books

import android.Manifest
import android.util.Size
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import top.dingfengbo.mylibrary.R

/**
 * ISBN capture: camera preview with on-device barcode detection, plus a manual entry fallback.
 *
 * The fallback is not decoration — it is the path used when the permission is denied, on a device
 * without a camera, and in the emulator, where the virtual camera cannot produce a real barcode.
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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
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
            onScanned(code)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (granted) {
                    BarcodePreview(onBarcode = deliver)
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(120.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.scan_permission_denied),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.scan_manual),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = {
                            manual = it.filter { ch -> ch.isDigit() }.take(13)
                            manualError = false
                        },
                        label = { Text(stringResource(R.string.book_detail_field_isbn)) },
                        singleLine = true,
                        isError = manualError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (manual.length == 10 || manual.length == 13) deliver(manual)
                            else manualError = true
                        },
                    ) { Text(stringResource(R.string.scan_manual_confirm)) }
                }
                if (manualError) {
                    Text(
                        text = stringResource(R.string.scan_manual_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

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
