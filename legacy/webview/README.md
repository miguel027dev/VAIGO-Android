# Archived WebView prototype

This activity belongs to the retired app.vienna.navigation package. The active app declares namespace/applicationId com.vano.nativeapp and launches .MainActivity from that namespace. Keeping the legacy activity under app/src/main/java incorrectly compiled unrelated WebView/ad dependencies and broke the current native build.

Its source is preserved here for historical review and is not part of the active APK. Reintroducing the WebView variant requires its own module, manifest, dependencies and security review.
