package at.rtr.rmbt.android.viewmodel

import android.widget.ArrayAdapter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.ui.viewstate.MapViewState
import at.specure.data.entity.MarkerMeasurementRecord
import at.specure.data.repository.MapRepository
import at.specure.location.LocationInfo
import at.specure.location.LocationState
import at.specure.location.LocationWatcher
import com.mapbox.mapboxsdk.geometry.LatLng
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class MapViewModel @Inject constructor(
    private val repository: MapRepository,
    private val locationWatcher: LocationWatcher
) : BaseViewModel() {

    private var filterTechnology: TechnologyFilter = TechnologyFilter.FILTER_ALL
    private var currentProvider: String
    val state = MapViewState()

    val locationLiveData: LiveData<LocationInfo?>
        get() = locationWatcher.liveData

    val locationStateLiveData: LiveData<LocationState?>
        get() = locationWatcher.stateLiveData

    lateinit var providersSpinnerAdapter: ArrayAdapter<String>
    private val basicProviderList = arrayListOf("All")
    val providersLiveData: MutableLiveData<List<String>> = MutableLiveData(basicProviderList)

    var markersLiveData: LiveData<List<MarkerMeasurementRecord>> =
        Transformations.switchMap(state.coordinatesLiveData) { repository.getMarkers(it?.latitude, it?.longitude, state.zoom.toInt()) }

    init {
        currentProvider = basicProviderList[0]
        addStateSaveHandler(state)
        obtainProviders()
    }

    private fun obtainProviders() {
        repository.obtainProviders {
            providersLiveData.postValue(it.apply { it.addAll(0, basicProviderList) })
        }
    }

    private fun obtainFilters(): List<String?> {
        val filterList = mutableListOf(null, null, null, TechnologyFilter.FILTER_ALL.filterValue, null, null, null)
        filterTechnology = TechnologyFilter.FILTER_ALL
        try {
            val technologyFilter = repository.active.technology
            if (technologyFilter != null) {
                filterList[FilterTypeCode.CODE_TECHNOLOGY.ordinal] = technologyFilter.technology
            }
        } catch (e: Exception) {
            Timber.e("Unable to load set map filters: ${e.localizedMessage}")
        }
        return filterList
    }

    fun loadMarkers(latitude: Double, longitude: Double, zoom: Int) {
        repository.loadMarkers(latitude, longitude, zoom) {
            state.coordinatesLiveData.postValue(LatLng(latitude, longitude))
        }
    }

    fun prepareDetailsLink(openUUID: String) = repository.prepareDetailsLink(openUUID)

    fun provideStyle(): String {
        return "mapbox://styles/specure/ckgqqcmvg51fj19qlisdg0vde"
    }

    fun buildCurrentLayersName(): List<String> {
        val filterList = obtainFilters()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val date = SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)

        val technology = filterList[FilterTypeCode.CODE_TECHNOLOGY.ordinal]?.toUpperCase(Locale.US) ?: TechnologyFilter.FILTER_ALL.filterValue.toUpperCase(Locale.US)

        return listOf(
            "C-$date-$technology-${currentProvider.toUpperCase(Locale.US)}",
            "M-$date-$technology-${currentProvider.toUpperCase(Locale.US)}",
            "H10-$date-$technology-${currentProvider.toUpperCase(Locale.US)}",
            "H1-$date-$technology-${currentProvider.toUpperCase(Locale.US)}",
            "H01-$date-$technology-${currentProvider.toUpperCase(Locale.US)}",
            "H001-$date-$technology-${currentProvider.toUpperCase(Locale.US)}"
        )
    }

    fun setTechnologyFilter(filterValue: TechnologyFilter) {
        repository.markTechnologyAsSelected(filterValue.filterValue)
    }

    fun setProvider(index: Int) {
        currentProvider = providersLiveData.value!![index]
    }

    fun obtainFiltersFromServer() {
        repository.obtainFilters {
            obtainFilters()
        }
    }
}

enum class TechnologyFilter(val colorId: Int, val filterValue: String) {
    FILTER_ALL(R.color.map_filter_technology_ALL, "all"),
    FILTER_2G(R.color.map_filter_technology_2G, "2G"),
    FILTER_3G(R.color.map_filter_technology_3G, "3G"),
    FILTER_4G(R.color.map_filter_technology_4G, "4G"),
    FILTER_5G(R.color.map_filter_technology_5G, "5G")
}

enum class FilterTypeCode {
    CODE_TYPE, // Mobile, WLAN, Browser
    CODE_SUBTYPE, // Upload, Download, Ping, Signal
    CODE_STATISTICS, // statistics method
    CODE_TECHNOLOGY, // 2G, 3G, ...
    CODE_OPERATOR, // operator (mobile networks)
    CODE_PERIOD, // period
    CODE_PROVIDER, // wlan, browser
}