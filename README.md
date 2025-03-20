# 🌿 Med Herb Lens

**Med Herb Lens** is an AI-powered Android application designed specifically for the identification of medicinal plants. Developed as a proof-of-concept, the app aims to bridge the gap between traditional herbal knowledge and modern deep learning technologies. It supports both offline and online modes, making it ideal for use in remote areas where medicinal flora are commonly found.

## 🚀 Features

- 📸 **Image-Based Recognition**
  - Real-time plant recognition via the camera
  - Static image analysis from the gallery
  - Batch image processing
  - On-device inference using TFLite for offline recognition

- 🔍 **Text-Based Search**
  - Find medicinal plants by entering Latin or Vietnamese names
  - Search powered by Algolia for fast and relevant results

- 🧠 **AI-Powered Identification**
  - EfficientNetB0 model trained on curated herb datasets
  - Lightweight and fast inference (~300-400ms per image)
  - Transfer learning and data augmentation for better accuracy

- 🌐 **Cloud-Hosted Knowledge Base**
  - Plant profiles with scientific/common names, medicinal uses, and images
  - Continuously updated by user contributions
  - Synced with Firebase Firestore

- 📡 **Offline Mode Support**
  - Identification and contributions work offline
  - Data synced automatically when connection is restored

- 🧑‍🤝‍🧑 **Community-Driven Contributions**
  - Upload new images
  - Review and verify AI predictions
  - Improve the dataset and model over time

- 🔐 **User Management and Notifications**
  - Firebase Authentication
  - Cloud Messaging for updates and sync alerts

## 🛠 Tech Stack

- Android (Kotlin, Jetpack Libraries, ViewModel, Navigation Component)
- Firebase (Firestore, Cloud Functions, Auth, Cloud Messaging)
- TensorFlow Lite (TFLite)
- ML Kit
- Algolia (Search)

## 📊 Model Performance

- Initial training on 7 species: 100% accuracy on validation set
- Model size optimized for mobile deployment
- Example recognition confidence:
  - *Cordyline fruticosa*: 68%
  - *Polyscias fruticosa*: 88%
  - *Stachytarpheta jamaicensis*: 98%
  - *Piper sarmentosum*: 100%

## 📈 Future Work

- Expand dataset with more plant species
- Improve model generalization and reduce misidentification
- Enhance UI/UX with user feedback
- Explore integration with DNA-based bioinformatics tools

## 📄 License

This project is published under the [CC BY-NC-ND 4.0 License](http://creativecommons.org/licenses/by-nc-nd/4.0/).
