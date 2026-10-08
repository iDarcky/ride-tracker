# Room KMP generated database constructor
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ML Kit text recognition: its components are created by reflection.
# Without these, R8 strips them and InputImage fails with a NullPointerException.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
