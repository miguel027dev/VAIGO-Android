package com.vano.nativeapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.*

private val VanoOrange = Color(0xFFFF9500)
private val VanoInk = Color(0xFF141414)
private val VanoPanel = Color(0xFFF8F8F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = VanoOrange, surface = VanoPanel)) { VanoApp() } }
    }
}

@Composable
private fun VanoApp() {
    val context = LocalContext.current
    val api = remember { VanoApi() }
    val scope = rememberCoroutineScope()
    var location by remember { mutableStateOf<Location?>(null) }
    var vehicle by remember { mutableStateOf(VehicleProfile.MOTORCYCLE) }
    var mode by remember { mutableStateOf(RouteMode.SMART) }
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf<List<SearchPlace>>(emptyList()) }
    var destination by remember { mutableStateOf<SearchPlace?>(null) }
    var routes by remember { mutableStateOf<List<RouteOption>>(emptyList()) }
    var selected by remember { mutableStateOf<RouteOption?>(null) }
    var navigating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mapHandle by remember { mutableStateOf<MapController?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startLocation(context) { location = it; mapHandle?.onLocation(it, vehicle, navigating) }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation(context) { location = it; mapHandle?.onLocation(it, vehicle, navigating) }
        } else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        runCatching { api.bootstrap() }.onFailure { message = "Servidor VANO indisponível: ${it.message}" }
    }
    LaunchedEffect(query, location) {
        searchJob?.cancel()
        if (query.length >= 3 && destination == null) {
            searchJob = scope.launch {
                delay(260)
                runCatching { api.search(query, location?.let { LatLngPoint(it.latitude, it.longitude) }) }
                    .onSuccess { search = it }
                    .onFailure { message = it.message }
            }
        } else if (query.length < 3) search = emptyList()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8E8E2))) {
        NativeMap(Modifier.fillMaxSize(), onReady = { mapHandle = it }, onGesture = { })

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SearchBar(
                query = query,
                onQuery = { query = it; destination = null },
                onClear = { query = ""; destination = null; routes = emptyList(); selected = null; mapHandle?.clearRoute() },
                vehicle = vehicle,
                onVehicle = { vehicle = it; location?.let { l -> mapHandle?.onLocation(l, vehicle, navigating) } },
            )
            AnimatedVisibility(search.isNotEmpty() && destination == null) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    LazyColumn(Modifier.heightIn(max = 330.dp)) {
                        items(search) { p ->
                            Row(Modifier.fillMaxWidth().clickable {
                                destination = p; query = p.name; search = emptyList(); mapHandle?.focus(p)
                            }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFF0D7)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Place, null, tint = VanoOrange) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VanoInk)
                                    Text("${p.category} · ${p.label}", fontSize = 12.sp, color = Color(0xFF696969), maxLines = 2)
                                }
                                p.distanceM?.let { Text(if (it < 1000) "${it} m" else "%.1f km".format(it / 1000.0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))

            if (destination != null && !navigating) {
                RouteSheet(destination!!, mode, { mode = it }, routes, selected, onSelect = { selected = it; mapHandle?.showRoute(it) }, busy = busy, onCalculate = {
                    val l = location ?: return@RouteSheet
                    scope.launch {
                        busy = true
                        runCatching { api.route(LatLngPoint(l.latitude, l.longitude), LatLngPoint(destination!!.lat, destination!!.lon), vehicle, mode, l.bearing.toDouble(), l.speed.toDouble()) }
                            .onSuccess { r -> routes = r; selected = r.firstOrNull(); selected?.let { mapHandle?.showRoute(it) } }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }, onStart = { if (selected != null) { navigating = true; location?.let { mapHandle?.onLocation(it, vehicle, true) } } })
            }
            if (navigating) NavigationHud(selected, vehicle, location, onRecenter = { location?.let { mapHandle?.recenter(it, vehicle) } }, onStop = { navigating = false })
        }

        message?.let {
            Snackbar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp), action = { TextButton(onClick = { message = null }) { Text("OK") } }) { Text(it) }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String)->Unit, onClear:()->Unit, vehicle: VehicleProfile, onVehicle:(VehicleProfile)->Unit) {
    Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 7.dp, color = Color.White) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = VanoOrange, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(value = query, onValueChange = onQuery, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = VanoInk, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f), decorationBox = { inner -> if (query.isEmpty()) Text("Para onde vamos?", color = Color(0xFF6F6F6F), fontSize = 17.sp); inner() },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions())
            if (query.isNotBlank()) IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Limpar") }
            FilterChip(selected = vehicle == VehicleProfile.MOTORCYCLE, onClick = { onVehicle(if (vehicle == VehicleProfile.MOTORCYCLE) VehicleProfile.CAR else VehicleProfile.MOTORCYCLE) },
                label = { Text(if (vehicle == VehicleProfile.MOTORCYCLE) "Moto" else "Carro", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(if (vehicle == VehicleProfile.MOTORCYCLE) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar, null) })
        }
    }
}

@Composable
private fun RouteSheet(dest: SearchPlace, mode: RouteMode, onMode:(RouteMode)->Unit, routes: List<RouteOption>, selected: RouteOption?, onSelect:(RouteOption)->Unit, busy:Boolean, onCalculate:()->Unit, onStart:()->Unit) {
    Card(Modifier.fillMaxWidth().navigationBarsPadding(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.width(46.dp).height(5.dp).clip(CircleShape).background(Color(0xFFD8D8D2)).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(dest.name, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            Text(dest.label, color = Color(0xFF666666), fontSize = 13.sp, maxLines = 2)
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Rápida", mode == RouteMode.FASTEST) { onMode(RouteMode.FASTEST) }
                ModeChip("Spark", mode == RouteMode.SMART) { onMode(RouteMode.SMART) }
                ModeChip("Segura", mode == RouteMode.SAFEST) { onMode(RouteMode.SAFEST) }
            }
            if (routes.isEmpty()) {
                Button(onClick = onCalculate, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = VanoOrange, contentColor = VanoInk), shape = RoundedCornerShape(18.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = VanoInk) else Text("Calcular rotas", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            } else {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    routes.take(3).forEach { r ->
                        Surface(Modifier.weight(1f).clickable { onSelect(r) }, shape = RoundedCornerShape(16.dp), color = if (selected?.id == r.id) Color(0xFFFFECD0) else Color(0xFFF3F3EF), border = if (selected?.id == r.id) androidx.compose.foundation.BorderStroke(2.dp, VanoOrange) else null) {
                            Column(Modifier.padding(10.dp)) { Text("${max(1, (r.durationSec/60).roundToInt())} min", fontWeight = FontWeight.ExtraBold); Text(r.label, fontSize = 11.sp); r.safetyScore?.let { Text("Seg. ${it.roundToInt()}", fontSize = 10.sp, color = Color(0xFF666666)) } }
                        }
                    }
                }
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = VanoOrange, contentColor = VanoInk), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Navigation, null); Spacer(Modifier.width(8.dp)); Text("Iniciar", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            }
        }
    }
}

@Composable private fun RowScope.ModeChip(text:String, active:Boolean, onClick:()->Unit) { Surface(Modifier.weight(1f).height(42.dp).clickable(onClick = onClick), shape = RoundedCornerShape(15.dp), color = if (active) VanoInk else Color(0xFFF1F1ED)) { Box(contentAlignment = Alignment.Center) { Text(text, color = if(active) Color.White else VanoInk, fontWeight=FontWeight.Bold, fontSize=13.sp) } } }

@Composable
private fun NavigationHud(route: RouteOption?, vehicle: VehicleProfile, location: Location?, onRecenter:()->Unit, onStop:()->Unit) {
    Card(Modifier.fillMaxWidth().navigationBarsPadding(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha=.97f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(VanoOrange), contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowUpward, null, tint = VanoInk, modifier=Modifier.size(34.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (vehicle == VehicleProfile.MOTORCYCLE) "Navegação Moto" else "Navegação Carro", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                val min = route?.durationSec?.div(60)?.roundToInt()
                Text(listOfNotNull(min?.let { "$it min" }, location?.speed?.times(3.6f)?.roundToInt()?.let { "$it km/h" }).joinToString(" · "), color = Color(0xFF606060), fontWeight=FontWeight.SemiBold)
            }
            FilledTonalIconButton(onClick = onRecenter, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.MyLocation, "Recalibrar") }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onStop, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Stop, "Parar") }
        }
    }
}

@Composable
private fun NativeMap(modifier: Modifier, onReady:(MapController)->Unit, onGesture:()->Unit) {
    val context = LocalContext.current
    AndroidView(modifier = modifier, factory = { ctx ->
        MapView(ctx).apply {
            onCreate(null)
            getMapAsync { map ->
                map.uiSettings.apply { isCompassEnabled = false; isLogoEnabled = false; isAttributionEnabled = true; isRotateGesturesEnabled = true; isTiltGesturesEnabled = true }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onGesture()
                }
                map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->
                    onReady(MapController(ctx, this, map, style))
                }
            }
            onStart(); onResume()
        }
    }, onRelease = { it.onPause(); it.onStop(); it.onDestroy() })
}

private class MapController(private val context: Context, private val view: MapView, private val map: MapLibreMap, private var style: Style) {
    private var marker: Marker? = null
    private var bearing = 0.0
    private var lastLocation: Location? = null
    private val routeSource = "vano-route-source"
    private val routeCasing = "vano-route-casing"
    private val routeLayer = "vano-route-line"
    init { ensureLayers() }
    private fun ensureLayers() {
        if (style.getSource(routeSource) == null) style.addSource(GeoJsonSource(routeSource, Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(0.0,0.0),Point.fromLngLat(0.0,0.0))))))
        if (style.getLayer(routeCasing) == null) style.addLayer(LineLayer(routeCasing, routeSource).withProperties(lineColor("#FFF7EC"), lineWidth(11f), lineOpacity(.92f), lineJoin("round"), lineCap("round")))
        if (style.getLayer(routeLayer) == null) style.addLayer(LineLayer(routeLayer, routeSource).withProperties(lineColor("#FF9500"), lineWidth(7f), lineOpacity(1f), lineJoin("round"), lineCap("round")))
    }
    fun showRoute(r: RouteOption) {
        val points = r.coordinates.map { Point.fromLngLat(it.lon, it.lat) }
        (style.getSource(routeSource) as? GeoJsonSource)?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
        if (points.isNotEmpty()) map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points.first().latitude(), points.first().longitude()), 15.8), 650)
    }
    fun clearRoute() { (style.getSource(routeSource) as? GeoJsonSource)?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(0.0,0.0),Point.fromLngLat(0.0,0.0))))) }
    fun focus(p: SearchPlace) { map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat,p.lon), 16.6), 650) }
    fun onLocation(l: Location, vehicle: VehicleProfile, navigating:Boolean) {
        lastLocation = l
        val pos = LatLng(l.latitude, l.longitude)
        if (marker == null) marker = map.addMarker(MarkerOptions().position(pos).icon(IconFactory.getInstance(context).fromBitmap(userPuckBitmap(context)))) else marker?.position = pos
        if (navigating) recenter(l, vehicle)
    }
    fun recenter(l: Location, vehicle: VehicleProfile) {
        val speed = l.speed.toDouble(); val desired = if (l.hasBearing()) l.bearing.toDouble() else bearing
        val target = NativeCameraEngine.target(vehicle, speed, l.accuracy.toDouble(), 0.0)
        bearing = NativeCameraEngine.smoothBearing(bearing, desired, target.bearingEase)
        val projected = project(l.latitude, l.longitude, bearing, target.lookAheadM)
        map.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(projected.first, projected.second)).zoom(target.zoom).tilt(target.pitch).bearing(bearing).build()), if (vehicle == VehicleProfile.MOTORCYCLE) 360 else 470)
    }
    private fun project(lat:Double, lon:Double, bearing:Double, meters:Double): Pair<Double,Double> {
        val R=6378137.0; val br=Math.toRadians(bearing); val d=meters/R; val p1=Math.toRadians(lat); val l1=Math.toRadians(lon)
        val p2=asin(sin(p1)*cos(d)+cos(p1)*sin(d)*cos(br)); val l2=l1+atan2(sin(br)*sin(d)*cos(p1),cos(d)-sin(p1)*sin(p2))
        return Math.toDegrees(p2) to Math.toDegrees(l2)
    }
}

private fun userPuckBitmap(context: Context): Bitmap {
    val d=context.resources.displayMetrics.density; val s=(54*d).roundToInt(); val b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888); val c=Canvas(b)
    val p=Paint(Paint.ANTI_ALIAS_FLAG); p.color=android.graphics.Color.WHITE; p.setShadowLayer(8*d,0f,2*d,0x44000000); c.drawCircle(s/2f,s/2f,s*.40f,p)
    p.clearShadowLayer(); p.color=0xFFFF9500.toInt(); c.drawCircle(s/2f,s/2f,s*.30f,p); p.color=0xFF171717.toInt();
    val path=android.graphics.Path().apply { moveTo(s*.50f,s*.20f); lineTo(s*.68f,s*.64f); lineTo(s*.50f,s*.56f); lineTo(s*.32f,s*.64f); close() }; c.drawPath(path,p); return b
}

@Suppress("MissingPermission")
private fun startLocation(context: Context, onLocation:(Location)->Unit) {
    val lm=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener=object: LocationListener { override fun onLocationChanged(location: Location) = onLocation(location) }
    runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(onLocation) }
    runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 1f, listener) }
    runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1800L, 4f, listener) }
}
