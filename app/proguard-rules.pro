# Add project specific ProGuard rules here.
# For MVP — minification is disabled. Enable and configure before production.

# Keep Google Maps classes
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
