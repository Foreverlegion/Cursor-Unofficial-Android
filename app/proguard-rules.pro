# Debug builds do not minify. Keep rules ready for a later release build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
