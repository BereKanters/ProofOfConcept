package com.example.proofofconcept

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var textResult: TextView
    private lateinit var correctionInput: EditText
    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var sharedPreferences: SharedPreferences
    private val corrections = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textResult = findViewById(R.id.text_result)
        correctionInput = findViewById(R.id.correction_input)
        previewView = findViewById(R.id.previewView)
        val classifyButton: Button = findViewById(R.id.btn_classify)
        val submitCorrectionButton: Button = findViewById(R.id.btn_submit_correction)

        sharedPreferences = getSharedPreferences("CorrectionsPrefs", MODE_PRIVATE)
        loadCorrections() // Load saved corrections

        // Load TensorFlow Lite model
        try {
            val options = Interpreter.Options().apply {
                setUseNNAPI(true) // Enable NNAPI for better performance
            }
            interpreter = Interpreter(loadModelFile(), options)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        classifyButton.setOnClickListener {
            previewView.visibility = View.VISIBLE
            startCamera()
        }

        submitCorrectionButton.setOnClickListener {
            submitCorrection()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }
            takePhoto()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (!::imageCapture.isInitialized) {
            Log.e("MainActivity", "ImageCapture is not initialized.")
            return
        }

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "${System.currentTimeMillis()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                    Toast.makeText(this@MainActivity, "Photo saved: $savedUri", Toast.LENGTH_SHORT).show()
                    val imageBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    imageBitmap?.let { classifyImage(it) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXApp", "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun classifyImage(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
        val output = Array(1) { FloatArray(1001) }
        interpreter.run(byteBuffer, output)
        displayResults(output[0])
    }

    private fun displayResults(output: FloatArray) {
        val labels = loadLabels()
        val topN = 5
        val predictions = output.indices
            .map { it to output[it] }
            .sortedByDescending { it.second }
            .take(topN)

        val resultText = predictions.joinToString("\n") { (index, confidence) ->
            val labelName = if (index in labels.indices) labels[index] else "Unknown"
            val correctedLabel = corrections[labelName] ?: labelName // Use corrected label if available
            "Label: $correctedLabel, Confidence: %.2f%%".format(confidence * 100)
        }
        textResult.text = resultText
    }

    private fun submitCorrection() {
        val originalLabel: String = textResult.text.toString()
            .split("\n")[0] // First prediction line
            .removePrefix("Label: ")
            .split(",")[0]
            .trim()
        val correctedLabel = correctionInput.text.toString().trim()

        if (correctedLabel.isNotEmpty()) {
            corrections[originalLabel] = correctedLabel
            saveCorrections()
            Toast.makeText(this, "Correction saved: $correctedLabel", Toast.LENGTH_SHORT).show()
            correctionInput.text.clear()
        } else {
            Toast.makeText(this, "Please enter a valid correction.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCorrections() {
        val editor = sharedPreferences.edit()
        corrections.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
    }

    private fun loadCorrections() {
        sharedPreferences.all.forEach { (key, value) ->
            if (value is String) {
                corrections[key] = value
            }
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
        for (pixelValue in intValues) {
            byteBuffer.putFloat((pixelValue shr 16 and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixelValue shr 8 and 0xFF) / 255.0f)
            byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
        }
        return byteBuffer
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("mobilenet_v1_1.0_224.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        assets.open("labels.txt").bufferedReader().useLines { lines ->
            lines.forEach { labels.add(it) }
        }
        return labels
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close()
        cameraExecutor.shutdown()
    }
}
