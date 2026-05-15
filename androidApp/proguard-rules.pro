# Project-specific ProGuard rules for toebeans.
# Default rules from AGP are applied first; the rules below are additive.

# kotlinx-datetime uses reflection-free serialization; no extra rules required.

# kotlinx-serialization JSON: keep the generated serializers for our model classes.
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static **$* *;
}

# SQLDelight generated code is already keep-safe by AGP defaults; included here as documentation.
-keep class app.toebeans.core.db.** { *; }

# Koin: keep modules and definitions. Koin 4.x uses no reflection at runtime for resolution,
# but `koin-compose` does reference Compose-specific classes — let AGP handle Compose minify.
