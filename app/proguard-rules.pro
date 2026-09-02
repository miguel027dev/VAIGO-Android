# Métodos expostos ao JavaScript do WebView precisam sobreviver ao R8.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Mantém nomes das pontes nativas usadas pelo conteúdo web.
-keep class app.vienna.navigation.MainActivity$ViennaNativeBridge { *; }
