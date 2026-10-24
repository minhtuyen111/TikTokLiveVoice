# Làm khó dịch ngược: đổi tên và gộp mọi lớp vào một gói, bỏ tên tệp nguồn/số dòng, xóa log.
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
-optimizationpasses 5

-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkParameterIsNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}

# Sherpa-ONNX gọi ngược từ JNI theo TÊN lớp/trường/hàm: bắt buộc giữ nguyên, nếu không giọng Việt tích hợp sẽ làm app văng.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.k2fsa.sherpa.onnx.**

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
