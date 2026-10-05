# R8 shrinks and obfuscates release builds. Libraries ship their own rules (Firebase, ML Kit,
# Mapbox, Compose, kotlinx.serialization, Ktor, SQLDelight); these cover what they don't.

# Readable crash reports: Crashlytics maps obfuscated names back with the uploaded mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager finds its Play services implementation by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
