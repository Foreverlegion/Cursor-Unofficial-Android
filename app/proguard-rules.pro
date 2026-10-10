# Consumer rules already cover this app:
# kotlinx.serialization (Companion.serializer; JSON names stay in the serializer),
# Retrofit, OkHttp, Compose, WorkManager (ListenableWorker names and constructors),
# and Tink protobuf fields used by EncryptedSharedPreferences.
# MainActivity and NoticeDismissReceiver are kept from the manifest.
# SafeLinks excludes MainActivity by that class name when it forces a browser,
# so cursor.com opens from this app do not resolve back to the VIEW filter.
# Tink references Error Prone annotations that are not on the Android classpath.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
