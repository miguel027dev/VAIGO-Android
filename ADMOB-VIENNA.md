# VIENNA Android — AdMob Native Advanced

- AdMob App ID: `ca-app-pub-8278559850014123~8752698301`
- Native production unit: `ca-app-pub-8278559850014123/5575870629`
- Google Mobile Ads SDK: `25.4.0`
- Debug builds automatically use Google's official native test unit.
- Release builds use the production native unit above.
- Ads are requested only when the web search-results page creates the VIENNA native ad slot.
- Navigation/map driving view never requests this native placement.

The server ZIP must be deployed together with this Android ZIP because the web layer owns the search-result placeholder and synchronizes its position with the native Android view.
