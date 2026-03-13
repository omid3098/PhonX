# Keep phonxcore entry points (combined Go module: Xray + Psiphon)
-keep class phonxcore.** { *; }
-dontwarn phonxcore.**

# Keep our app classes
-keep class ir.phonx.** { *; }

# Keep Android VpnService
-keep class * extends android.net.VpnService { *; }

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
