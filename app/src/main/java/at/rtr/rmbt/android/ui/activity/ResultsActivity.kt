package at.rtr.rmbt.android.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import at.rtr.rmbt.android.R
import at.rtr.rmbt.android.databinding.ActivityResultsBinding
import at.rtr.rmbt.android.di.viewModelLazy
import at.rtr.rmbt.android.map.wrapper.LatLngW
import at.rtr.rmbt.android.map.wrapper.MapWrapper
import at.rtr.rmbt.android.ui.adapter.QosResultAdapter
import at.rtr.rmbt.android.ui.adapter.ResultChartFragmentPagerAdapter
import at.rtr.rmbt.android.ui.adapter.ResultQoEAdapter
import at.rtr.rmbt.android.util.isGmsAvailable
import at.rtr.rmbt.android.util.isHmsAvailable
import at.rtr.rmbt.android.util.listen
import at.rtr.rmbt.android.viewmodel.ResultViewModel
import at.specure.data.NetworkTypeCompat
import at.specure.data.entity.TestResultRecord
import timber.log.Timber

class ResultsActivity : BaseActivity() {

    private val viewModel: ResultViewModel by viewModelLazy()
    private lateinit var binding: ActivityResultsBinding
    private val adapter: ResultQoEAdapter by lazy { ResultQoEAdapter() }
    private val qosAdapter: QosResultAdapter by lazy { QosResultAdapter() }
    private lateinit var resultChartFragmentPagerAdapter: ResultChartFragmentPagerAdapter

    private var mapLoadRequested: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindContentView(R.layout.activity_results)
        binding.state = viewModel.state

        viewModel.state.playServicesAvailable.set(isGmsAvailable() || isHmsAvailable())

        binding.map.onCreate(savedInstanceState)

        val testUUID = intent.getStringExtra(KEY_TEST_UUID)
        check(!testUUID.isNullOrEmpty()) { "TestUUID was not passed to result activity" }

        val returnPoint = intent.getStringExtra(KEY_RETURN_POINT)
        check(!testUUID.isNullOrEmpty()) { "ReturnPoint was not passed to result activity" }

        binding.viewPagerCharts?.offscreenPageLimit = 3
        binding.viewPagerCharts?.let { viewPagerCharts ->
            binding.tabLayoutCharts?.setupWithViewPager(viewPagerCharts, true)
        }

        viewModel.state.testUUID = testUUID
        viewModel.state.returnPoint = returnPoint?.let { ReturnPoint.valueOf(returnPoint) } ?: ReturnPoint.HOME
        viewModel.testServerResultLiveData.listen(this) { result ->
            viewModel.state.testResult.set(result)

            result?.testOpenUUID?.let {
                resultChartFragmentPagerAdapter = ResultChartFragmentPagerAdapter(supportFragmentManager, testUUID, result.networkType)
                binding.viewPagerCharts?.adapter = resultChartFragmentPagerAdapter
            }

            result?.let {
                if (!mapLoadRequested) {
                    mapLoadRequested = true
                    binding.map.loadMapAsync {
                        setUpMap(it)
                    }
                }
            }
        }
        viewModel.testResultDetailsLiveData.listen(this) {
            Timber.d("found ${it.size} rows of details")
            // todo: display result details
        }

        viewModel.qoeResultLiveData.listen(this) {
            viewModel.state.qoeRecords.set(it)
            adapter.submitList(it)
        }

        binding.qoeResultsRecyclerView?.adapter = adapter

        binding.qoeResultsRecyclerView?.apply {
            val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            ContextCompat.getDrawable(context, R.drawable.history_item_divider)?.let {
                itemDecoration.setDrawable(it)
            }
            binding.qoeResultsRecyclerView?.addItemDecoration(itemDecoration)
        }
        binding.buttonBack.setOnClickListener {
            onBackPressed()
        }
        binding.buttonShare.setOnClickListener {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_TEXT, viewModel.state.testResult.get()?.shareText)
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, viewModel.state.testResult.get()?.shareTitle)
            shareIntent.type = "text/plain"
            startActivity(Intent.createChooser(shareIntent, null))
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshResults()
        }

        viewModel.loadingLiveData.listen(this) {
            binding.swipeRefreshLayout.isRefreshing = false
            if (viewModel.state.testResult.get() == null) {
                binding.textFailedToLoad.visibility = if (it) View.GONE else View.VISIBLE
            } else {
                binding.textFailedToLoad.visibility = View.GONE
            }
        }

        binding.labelTestResultDetail?.setOnClickListener {
            TestResultDetailActivity.start(this, viewModel.state.testUUID)
        }

        binding.qosResultsRecyclerView?.apply {
            adapter = qosAdapter
            val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            ContextCompat.getDrawable(context, R.drawable.history_item_divider)?.let {
                itemDecoration.setDrawable(it)
            }
            binding.qosResultsRecyclerView?.addItemDecoration(itemDecoration)
        }

        viewModel.qosCategoryResultLiveData.listen(this) {
            viewModel.state.qosCategoryRecords.set(it)
            qosAdapter.submitList(it)
        }

        qosAdapter.actionCallback = {

            QosTestsSummaryActivity.start(this, it)
        }
        refreshResults()
    }

    private fun setUpMap(result: TestResultRecord) {
        if (result.latitude != null && result.longitude != null) {
            val latLngW = LatLngW(result.latitude!!, result.longitude!!)

            val icon = when (result.networkType) {
                NetworkTypeCompat.TYPE_BLUETOOTH,
                NetworkTypeCompat.TYPE_VPN,
                NetworkTypeCompat.TYPE_UNKNOWN -> R.drawable.ic_marker_empty
                NetworkTypeCompat.TYPE_LAN -> R.drawable.ic_marker_ethernet
                NetworkTypeCompat.TYPE_BROWSER -> R.drawable.ic_marker_browser
                NetworkTypeCompat.TYPE_WLAN -> R.drawable.ic_marker_wifi
                NetworkTypeCompat.TYPE_5G_AVAILABLE,
                NetworkTypeCompat.TYPE_4G -> R.drawable.ic_marker_4g
                NetworkTypeCompat.TYPE_3G -> R.drawable.ic_marker_3g
                NetworkTypeCompat.TYPE_2G -> R.drawable.ic_marker_2g
                NetworkTypeCompat.TYPE_5G_NSA,
                NetworkTypeCompat.TYPE_5G -> R.drawable.ic_marker_5g
            }

            mapW().run {
                addCircle(
                    latLngW,
                    ContextCompat.getColor(this@ResultsActivity, R.color.map_circle_fill),
                    ContextCompat.getColor(this@ResultsActivity, R.color.map_circle_stroke),
                    STROKE_WIDTH, CIRCLE_RADIUS
                )
                addMarker(this@ResultsActivity, latLngW, ANCHOR_U, ANCHOR_V, icon)
                moveCamera(latLngW, ZOOM_LEVEL)
                setOnMapClickListener {
                    DetailedFullscreenMapActivity.start(
                        this@ResultsActivity,
                        it.latitude,
                        it.longitude,
                        result.networkType
                    )
                }
            }
        }
    }

    private fun mapW(): MapWrapper = binding.map.mapWrapper

    private fun refreshResults() {
        viewModel.loadTestResults()
        binding.swipeRefreshLayout.isRefreshing = true
    }

    override fun onStart() {
        super.onStart()
        binding.map?.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.map?.onResume()
    }

    override fun onStop() {
        super.onStop()
        binding.map?.onStop()
    }

    override fun onPause() {
        binding.map?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.map.onDestroy()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        when (viewModel.state.returnPoint) {
            ReturnPoint.HOME -> HomeActivity.startWithFragment(this, HomeActivity.Companion.HomeNavigationTarget.HOME_FRAGMENT_TO_SHOW)
            ReturnPoint.HISTORY -> HomeActivity.startWithFragment(this, HomeActivity.Companion.HomeNavigationTarget.HISTORY_FRAGMENT_TO_SHOW)
        }
    }

    enum class ReturnPoint {
        HOME,
        HISTORY;
    }

    companion object {

        private const val ZOOM_LEVEL = 15f
        private const val CIRCLE_RADIUS = 13.0
        private const val STROKE_WIDTH = 7f
        private const val ANCHOR_U = 0.5f
        private const val ANCHOR_V = 0.865f

        private const val KEY_TEST_UUID = "KEY_TEST_UUID"
        private const val KEY_RETURN_POINT = "KEY_RETURN_POINT"

        fun start(context: Context, testUUID: String, returnPoint: ReturnPoint) {
            val intent = Intent(context, ResultsActivity::class.java)
            intent.putExtra(KEY_TEST_UUID, testUUID)
            intent.putExtra(KEY_RETURN_POINT, returnPoint.name)
            context.startActivity(intent)
        }
    }
}