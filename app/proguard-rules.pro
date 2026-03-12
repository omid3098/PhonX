# Keep Xray/libv2ray entry points
-keep class com.v2ray.** { *; }
-keep class libv2ray.** { *; }
-dontwarn com.v2ray.**
-dontwarn libv2ray.**

# Psiphon is not included (native lib conflict with libv2ray). Keep rules for future re-integration.
-dontwarn ca.psiphon.**
-dontwarn psi.**

# Keep our app classes
-keep class ir.phonx.** { *; }

# Keep Android VpnService
-keep class * extends android.net.VpnService { *; }

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
