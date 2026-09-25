# Keep model constructors/properties used by Firebase's reflection mapper.
-keepattributes Signature,*Annotation*
-keepclassmembers class com.amisayem.kothabolbo.domain.model.** {
    <fields>;
    <init>(...);
}
-keep class com.google.firebase.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Drive token acquisition references optional legacy auth internals.
-keep class com.google.android.gms.auth.** { *; }
