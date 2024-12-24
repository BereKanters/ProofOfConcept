package com.example.proofofconcept

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
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
    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService

    private var currentPrediction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textResult = findViewById(R.id.text_result)
        previewView = findViewById(R.id.previewView)
        val classifyButton: Button = findViewById(R.id.btn_classify)

        // Load the TensorFlow Lite model
        try {
            interpreter = Interpreter(loadModelFile())
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Initially, the PreviewView is invisible. We will show it when the button is pressed.
        classifyButton.setOnClickListener {
            previewView.visibility = View.VISIBLE // Make PreviewView visible after button press
            startCamera() // Start the camera feed
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Set up Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Set up ImageCapture
            imageCapture = ImageCapture.Builder().build()

            // Select the back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to lifecycle
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraXApp", "Use case binding failed", e)
            }

            // Now that the camera is ready, you can take a photo
            takePhoto() // Now it's safe to call takePhoto() after camera setup
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Ensure imageCapture is initialized before using it
        if (!::imageCapture.isInitialized) {
            Log.e("MainActivity", "ImageCapture is not initialized.")
            return
        }

        // Create a file to save the image
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

                    // Decode the image and classify it
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
        // Resize image to fit model input
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Prepare the output array to store prediction results
        val output = Array(1) { FloatArray(1001) }  // 1001 classes for ImageNet
        interpreter.run(byteBuffer, output)

        // Process the result
        displayResults(output[0])
    }

    private fun displayResults(output: FloatArray) {
        val labels = loadLabels()  // Load class labels from 'labels.txt'

        // Find the index of the max value in the output array (classification result)
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: -1
        val confidence = output[maxIndex] * 100  // Convert to percentage

        // Get the predicted label from the labels list
        var labelName = if (maxIndex in labels.indices) labels[maxIndex] else "Unknown"

        // If the confidence is too low, don't display a label
        if (confidence < 10.0) {
            labelName = ""  // Leave label empty if confidence is low
        }

        // Update the UI with the classification result
        textResult.text = if (labelName.isNotEmpty()) {
            "Label: $labelName, Confidence: %.2f%%".format(confidence)
        } else {
            "Classification confidence too low"
        }


    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)
        bitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
        for (pixelValue in intValues) {
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 127.5f)
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 127.5f)
            byteBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 127.5f)
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
        interpreter.close() // Don't forget to close the interpreter when done
    }
}


