# ---- Xposed 入口（按名字反射加载，必须保留）----
-keep class com.upuaut.xposedsearch.XposedInit { *; }
-keep class com.upuaut.xposedsearch.hooks.** { *; }
-keep class com.upuaut.xposedsearch.coui.** { *; }

# ---- 通过 XposedHelpers 以字符串反射的方法/字段所属类 ----
-keep class com.upuaut.xposedsearch.ui.MainViewModelKt { *; }
-keepclassmembers class com.upuaut.xposedsearch.** {
    public static <fields>;
}
-keepnames class com.upuaut.xposedsearch.**

# ---- 全项目：保留枚举、可序列化字段名 ----
-keepclassmembers enum * { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# ---- Compose 框架 ----
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }

# ---- COUI（Compose Multiplatform，含反射查找的 Composable 单例）----
-keep class io.github.suqi8.coui.kmp.** { *; }
-dontwarn io.github.suqi8.coui.**

# ---- Kotlin 元数据 ----
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class kotlin.Metadata { *; }

# ---- LSPosed / Xposed API 仅在编译期提供 ----
-dontwarn de.robv.android.xposed.**

# ---- 通用 ----
-dontwarn org.jetbrains.**
-dontwarn com.google.**
