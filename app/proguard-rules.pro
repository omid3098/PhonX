# Keep phonxcore entry points (combined Go module: Xray + Psiphon)
-keep class phonxcore.** { *; }
-dontwarn phonxcore.**

# Keep Go mobile runtime (go.Seq, go.Universe — used by JNI in libgojni.so)
-keep class go.** { *; }

# Keep our app classes
-keep class ir.phonx.** { *; }

# Keep Android VpnService
-keep class * extends android.net.VpnService { *; }

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
