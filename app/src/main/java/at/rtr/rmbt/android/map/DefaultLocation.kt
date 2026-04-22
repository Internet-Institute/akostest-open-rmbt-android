package at.rtr.rmbt.android.map

import at.rtr.rmbt.android.map.wrapper.LatLngW
import com.google.android.gms.maps.model.LatLng

private const val DEFAULT_LAT: Double = (46.46694444F + 46.28361111F) / 2.0
private const val DEFAULT_LONG: Double = (14.1549516F + 15.6950152F) / 2.0
private const val DEFAULT_ZOOM_LEVEL = 7F

object DefaultLocation {

    val sloveniaLocationWrapped = LatLngW(DEFAULT_LAT, DEFAULT_LONG)
    val sloveniaLocation = LatLng(DEFAULT_LAT, DEFAULT_LONG)
    val sloveniaZoomLevel = DEFAULT_ZOOM_LEVEL

    val defaultMaximumZoomLevelForCoverage = 19f
}



