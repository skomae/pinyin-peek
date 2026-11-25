# ML Kit + Room keep
-keep class com.google.mlkit.** { *; }
-keep class androidx.room.** { *; }
-dontwarn com.google.mlkit.**

# HanLP: uses reflection over its own classes and reads bundled data from
# the classpath; keep it intact under R8.
-keep class com.hankcs.hanlp.** { *; }
-dontwarn com.hankcs.hanlp.**
