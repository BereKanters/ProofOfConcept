# **Proof of Concept: Image Classification with Machine Learning**

This is a mobile application that demonstrates the use of **Machine Learning** for image classification. The app uses a pre-trained TensorFlow Lite model to classify images captured by the device's camera. It includes a dynamic feedback mechanism where users can correct misclassifications and improve future predictions.

---

## **Features**
1. **Image Classification**:
   - Captures images using the device camera.
   - Classifies the image using a pre-trained **MobileNet** model.
   - Displays the predicted label and confidence score.

2. **Dynamic Corrections**:
   - Users can correct misclassifications by providing the correct label.
   - Corrections are saved locally and applied dynamically to future predictions.

3. **Feedback Mechanism**:
   - A green "Correct" button allows users to confirm accurate predictions.
   - A corrections file maintains a mapping of incorrect-to-correct labels.

---

## **Technologies Used**
- **Android Studio**: Primary IDE for development.
- **Kotlin**: Programming language for Android development.
- **TensorFlow Lite**: Lightweight ML model framework for on-device inference.
- **MobileNet Model**: Pre-trained image classification model.
- **Custom Feedback System**: Implements dynamic user-driven corrections.

---

## **Getting Started**

### Prerequisites
- **Android Studio**: Download and install the latest version.
- **Gradle Build System**: Ensure Gradle is set up correctly in Android Studio.
- **TensorFlow Lite Model**: Included in the `assets` folder (`mobilenet_v1_1.0_224.tflite`).
- **Labels File**: Included in the `assets` folder (`labels.txt`).

---

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/BereKanters/ProofOfConcept.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and ensure all dependencies are installed.

### Run the App
1. Connect an Android device or start an emulator.
2. Build and run the project from **Android Studio**.
3. Use the camera to capture images and view predictions.

---

## **Usage**
1. **Classify Images**:
   - Tap the "Classify" button to capture an image.
   - View the predicted label and confidence score.

2. **Correct Predictions**:
   - If the prediction is incorrect:
     - Enter the correct label in the text field.
     - Tap the "Submit Correction" button.
   - The correction will be saved and applied to future predictions.

3. **View Corrections**:
   - Tap the "Read Corrections" button to see all saved corrections.

4. **Confirm Correct Predictions**:
   - If the prediction is accurate, tap the green "Correct" button.

---

## **Dynamic Feedback System**
The app simulates a learning process by allowing users to submit corrections for misclassifications. Here's how it works:
1. **Misclassified Labels**:
   - If a label is incorrect (e.g., "BandAid" instead of "CD"), users can enter the correct label.
   - The correction is stored in the `corrections.txt` file.

2. **Dynamic Application**:
   - On every new prediction, the app checks the corrections file and applies any relevant corrections.
   - For example, if "BandAid" was corrected to "CD," the app will display "CD" for future classifications.

3. **Corrections File**:
   - Corrections are stored in a simple text format: `IncorrectLabel -> CorrectLabel`.

---
## **DEMO**
![Demo of the Application](images/IMG_4078.gif)

