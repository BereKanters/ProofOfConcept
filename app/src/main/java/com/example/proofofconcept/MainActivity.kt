package com.example.proofofconcept

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var interpreter: Interpreter
    private lateinit var textResult: TextView
    private lateinit var editCorrectLabel: EditText
    private lateinit var btnCorrectLabel: Button
    private lateinit var btnCorrect: Button
    private lateinit var btnReadCorrections: Button
    private lateinit var textFeedback: TextView
    private val correctionsMap = mutableMapOf<String, String>()
    private var currentPrediction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textResult = findViewById(R.id.text_result)
        editCorrectLabel = findViewById(R.id.edit_correct_label)
        btnCorrectLabel = findViewById(R.id.btn_correct_label)
        btnCorrect = findViewById(R.id.btn_correct)
        btnReadCorrections = findViewById(R.id.btn_read_corrections)
        textFeedback = findViewById(R.id.text_feedback)

        val classifyButton: Button = findViewById(R.id.btn_classify)

        // Load the TensorFlow Lite model
        try {
            interpreter = Interpreter(loadModelFile())
            loadCorrectionsFromJson() // Load corrections from file into memory
        } catch (e: IOException) {
            e.printStackTrace()
        }

        classifyButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
                )
            } else {
                launchCameraIntent()
            }
        }

        btnCorrectLabel.setOnClickListener { handleCorrection() }

        btnCorrect.setOnClickListener {
            textFeedback.text = "Great! The prediction is correct."
            textFeedback.visibility = View.VISIBLE
        }

        btnReadCorrections.setOnClickListener {
            val corrections = readCorrections()
            textFeedback.text = corrections
            textFeedback.visibility = View.VISIBLE
        }
    }

    private fun launchCameraIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && data != null) {
            val extras = data.extras
            val imageBitmap = extras?.get("data") as Bitmap?
            imageBitmap?.let { classifyImage(it) }
        }
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
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: -1
        val confidence = output[maxIndex] * 100
        var labelName = if (maxIndex in labels.indices) labels[maxIndex] else "Unknown"

        // Apply corrections dynamically
        labelName = correctionsMap[labelName] ?: labelName

        // Confidence threshold check
        if (confidence < 50.0) {
            labelName = "Uncertain"
        }

        currentPrediction = labelName
        textResult.text = "Label: $labelName, Confidence: %.2f%%".format(confidence)

        // Adjust visibility
        toggleCorrectionVisibility(confidence < 50.0)
        textFeedback.visibility = View.GONE
    }

    private fun handleCorrection() {
        val correctLabel = editCorrectLabel.text.toString().trim()
        if (correctLabel.isEmpty()) {
            textFeedback.text = "Please enter the correct label."
            textFeedback.visibility = View.VISIBLE
            return
        }

        if (currentPrediction != null) {
            val wrongLabel = currentPrediction!!
            correctionsMap[wrongLabel] = correctLabel // Update in-memory map
            saveCorrectionsToJson() // Save to file

            // Override the displayed result immediately
            currentPrediction = correctLabel
            textResult.text = "Label: $correctLabel, Confidence: 100% (Corrected)"
            textFeedback.text = "Correction submitted: $wrongLabel -> $correctLabel"
            textFeedback.visibility = View.VISIBLE
            editCorrectLabel.text.clear()
        }
    }

    private fun saveCorrectionsToJson() {
        val json = JSONObject(correctionsMap as Map<*, *>?).toString()
        File(filesDir, "corrections.json").writeText(json)
    }

    private fun loadCorrectionsFromJson() {
        val file = File(filesDir, "corrections.json")
        if (file.exists()) {
            val json = JSONObject(file.readText())
            json.keys().forEach { key -> correctionsMap[key] = json.getString(key) }
        }
    }

    private fun readCorrections(): String {
        return correctionsMap.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
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
        return try {
            assets.open("labels.txt").bufferedReader().useLines { it.toList() }
        } catch (e: IOException) {
            Log.e("MainActivity", "Error loading labels file", e)
            emptyList()
        }
    }

    private fun toggleCorrectionVisibility(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        editCorrectLabel.visibility = visibility
        btnCorrectLabel.visibility = visibility
        btnCorrect.visibility = visibility
    }

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_CAMERA_PERMISSION = 2
    }
}
