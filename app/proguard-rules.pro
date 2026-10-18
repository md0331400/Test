-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class com.amisayem.kothabolbo.bridge.** { *; }
-keep class com.google.firebase.** { *; }

# Google Drive Java client includes optional desktop/JNDI/Kerberos Apache HTTP paths.
# They are not used on Android and must be ignored by R8.
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.impl.auth.**

