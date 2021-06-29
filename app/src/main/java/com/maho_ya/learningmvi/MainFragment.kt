package com.maho_ya.learningmvi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.maho_ya.learningmvi.databinding.FragmentMainBinding
import com.maho_ya.learningmvi.mvi.MviView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// AndroidコンポーネントへのDI用。Fragmentの場合、Activityにも同じアノテーションが必要
// 設定したクラスごとにHiltコンポーネントが生成され、親階層から依存家計が受け取れるようになる
@AndroidEntryPoint
class MainFragment : Fragment(), MviView<BarcodeState> {

    private val barcodeViewModel: BarcodeViewModel by viewModels()
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var binding: FragmentMainBinding
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = getOutputDirectory()

        barcodeViewModel.state.observe(viewLifecycleOwner,
            {
                render(it)
            }
        )

        lifecycleScope.launch {
            // 待機状態
            barcodeViewModel.intents.send(BarcodeIntent.Idle)
        }

        checkCameraPermission {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun render(state: BarcodeState) {

        with(state) {
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE

            // QRコードからurlを取得した場合、ブラウザを開く
            if (url != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                }
            }
            if (errorMessage != null) ToastUtil.show(
                requireContext(),
                state.errorMessage.toString()
            )
        }
    }

    private fun getOutputDirectory(): File {
        // TODO: Android10以上用にcontentResolverを使用するよう修正する
        val mediaDir = requireActivity().externalMediaDirs.firstOrNull()?.let {
            // app_nameの名称でディレクトリ作成
            File(it, resources.getString(R.string.app_name)).apply {
                // ディレクトリがない場合、親ディレクトリ含めて作成する
                mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else requireActivity().filesDir
    }

    private fun startCamera() {
        // カメラのライフサイクルを制御できるインスタンスを作成
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // カメラのライフサイクルをLifeCycleOwnerアプリケーションのプロセスにバインドする
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // PreviewViewのsurfaceProviderをプレビューに設定する。
            // surfaceProviderはライフサイクルにバインドされた時に、カメラフィードが開始されるようにできるもの
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // プレビューの画像を解析して、ここでは輝度の平均値をログに出力している。
            val imageAnalysis = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        LuminosityAnalyzer { barcode ->
                            lifecycleScope.launch {
                                barcodeViewModel.intents.send(BarcodeIntent.ScanBarcode(barcode))
                            }
                        }
                    )
                }

            // 背面カメラを選択
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // ImageCaptureの初期化。takePhoto内で保存などの設定がされる
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.apply {
                    // バインドされていない状態にリセットする
                    unbindAll()
                    // ProcessCameraProviderに CameraSelector, UseCase(今回はPreview, ImageCapture, imageAnalysis) をバインドする
                    // Fragmentはライフサイクルがビューのライフサイクルより長くなることがあるため、viewLifecycleOwnerを使用
                    // https://developer.android.com/reference/androidx/fragment/app/Fragment#getViewLifecycleOwner()
                    val camera = bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )

                    setAutoFocusWithTaped(camera)
                    setAutoFocus(camera)
                }

            } catch (ex: Exception) {
                // アプリのフォーカスがなくなるなどExceptionが発行されるパターンがあるためcatchしておく
                Timber.e(ex, "Use case binding failed")
            }

            // メインスレッドで実行
        }, ContextCompat.getMainExecutor(requireContext()))


    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setAutoFocusWithTaped(camera: Camera) {
        binding.previewView.afterMeasured {
            binding.previewView.setOnTouchListener { _, event ->
                return@setOnTouchListener when (event.action) {
                    MotionEvent.ACTION_DOWN -> true
                    MotionEvent.ACTION_UP -> {
                        camera.cameraControl.cancelFocusAndMetering()
                        // フォーカスと計測するためにセンサー座標指定用のMeteringPoint生成するためのFactoryを取得する
                        val factory = SurfaceOrientedMeteringPointFactory(
                            binding.previewView.width.toFloat(),
                            binding.previewView.height.toFloat()
                        )
                        // タップ位置からMeteringPointを生成
                        val point = factory.createPoint(event.x, event.y)
                        // オートフォーカスと測光用のアクションをトリガーするFocusMeteringActionを生成
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            // 自動キャンセルのディレイ秒数
                            .setAutoCancelDuration(5, TimeUnit.SECONDS)
                            .build()
                        // 引数に設定したアクションを開始。オートフォーカスと測光が有効になる
                        val future = camera.cameraControl.startFocusAndMetering(action)
                        future.addListener(
                            {
                                // オートフォーカスの結果を取得
                                val result = future.get()
                                Timber.v("future ${result.isFocusSuccessful}")
                            }, cameraExecutor
                        )
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setAutoFocus(camera: Camera) {
        binding.previewView.afterMeasured {

            val autoFocusPoint = SurfaceOrientedMeteringPointFactory(1f, 1f)
                .createPoint(.5f, .5f)
            try {
                val autoFocusAction = FocusMeteringAction.Builder(
                    autoFocusPoint,
                    FocusMeteringAction.FLAG_AF
                ).apply {
                    //start auto-focusing after 2 seconds
                    setAutoCancelDuration(2, TimeUnit.SECONDS)
                }.build()
                camera.cameraControl.startFocusAndMetering(autoFocusAction)
            } catch (ex: CameraInfoUnavailableException) {
                Timber.d(ex, "cannot access camera")
            }
        }
    }

    private fun checkCameraPermission(func: () -> Unit) {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            func()
            return
        }

        if (shouldShowRequestPermissionRationale(permission)) {
            ToastUtil.show(
                requireContext(),
                "設定からカメラパーミッションを許可してください。"
            )
            return
        }

        actionRequestPermission(func).launch(permission)
    }

    // Request camera permission
    private fun actionRequestPermission(func: () -> Unit) =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                func()
            } else {
                ToastUtil.show(
                    requireContext(),
                    "Permissions not granted by the user."
                )
                requireActivity().finish()
            }
        }
}

private class LuminosityAnalyzer(
    private val intentsSend: (barcode: Barcode) -> Unit
) : ImageAnalysis.Analyzer {

    private var url: String? = null

    // バッファーに格納されているデータをByteArrayにして返す
    private fun ByteBuffer.toByteArray(): ByteArray {
        // バッファーに格納されている値を再度読み込めるように、位置をゼロに戻す
        rewind()
        // バッファー分のByteArrayを作成
        // remaining(): バッファーの残りの要素数を返す
        val data = ByteArray(remaining())
        // dataにバッファのバイトを転送する
        get(data)
        return data
    }

    // imageProxy.imageで必要なSuppressLint
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // 読み込み対象をQRコードのみに限定。限定することで読み込み速度を早く出来る
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            // BarcodeScanningのインスタンスを取得
            val scanner = BarcodeScanning.getClient(options)
            val result = scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        when (barcode.valueType) {
                            Barcode.TYPE_WIFI -> {
                                val ssid = barcode.wifi!!.ssid
                                val password = barcode.wifi!!.password
                                val type = barcode.wifi!!.encryptionType
                            }
                            Barcode.TYPE_URL -> {
                                if (url != null) return@addOnSuccessListener

                                val title = barcode.url!!.title
                                url = barcode.url!!.url
                                Timber.v(url!!.toString())

                                intentsSend(barcode)
                            }
                        }
                    }
                }
                .addOnFailureListener {
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}

// サイズが確定したらラムダを実行
// crossinline 渡したラムダを別のコンテキストで使用する場合に必要
inline fun View.afterMeasured(crossinline block: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            }
        }
    )
}
