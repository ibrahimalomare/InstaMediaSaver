# Keep Kotlinx Serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.instamediasaver.**$$serializer { *; }
-keepclassmembers class com.example.instamediasaver.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.instamediasaver.** {
    kotlinx.serialization.KSerializer serializer(...);
}
