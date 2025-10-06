# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ===================================================================
#           核心 KEEP 规则 - 解决运行时闪退
# ===================================================================

# 保持所有类的无参数构造函数，防止 R8 错误地移除它们。
# 许多库（如 POI）依赖反射来创建实例。
-keep class * {
    <init>();
}


# --- 1. 保持你项目中的所有数据模型和关键类 ---
# 这条规则对于 Gson, Room 等至关重要！
-keep class com.apollomonasa.scheduleapp.** { *; }
-keep interface com.apollomonasa.scheduleapp.** { *; }

# --- 2. Apache POI 库的 KEEP 规则 ---
# 保持 POI 自身及其依赖的核心库不被混淆和移除。
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep interface javax.xml.stream.** { *; }
-keep class javax.xml.stream.** { *; }
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class com.zaxxer.sparsebits.** { *; }

# --- 3. Gson 库的 KEEP 规则 ---
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.annotations.** { *; }

# --- 4. Kotlin Coroutines 内部类 ---
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepnames class kotlinx.coroutines.flow.internal.** { *; }

# ===================================================================
#           DONTWARN 规则 - 解决 Release 编译失败
# ===================================================================
# 告诉 R8 忽略所有 Apache POI 引用的、但在 Android 项目中不存在的可选依赖库。
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.w3c.dom.events.**

# --- POI 自身及其直接依赖 ---
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn org.apache.commons.logging.**

# --- POI 的可选压缩算法依赖 ---
-dontwarn org.apache.commons.compress.compressors.brotli.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**

# --- POI 的可选日志和 OSGi 依赖 ---
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn org.osgi.framework.**

# --- POI 的可选字节码操作依赖 (pack200) ---
-dontwarn org.objectweb.asm.**

# ===================================================================
#               保留已有的 Room 数据库规则
# ===================================================================
-keep class * implements androidx.room.Dao
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
}