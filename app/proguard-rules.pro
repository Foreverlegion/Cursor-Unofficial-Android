# Consumer rules already cover this app:
# kotlinx.serialization (Companion.serializer; JSON names stay in the serializer),
# Retrofit, OkHttp, Compose, WorkManager (ListenableWorker names and constructors),
# and Tink protobuf fields used by EncryptedSharedPreferences.
# MainActivity and NoticeDismissReceiver are kept from the manifest.
# Tink references Error Prone annotations that are not on the Android classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
