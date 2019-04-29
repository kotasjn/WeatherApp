package com.fhv.weatherapp

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProviders
import android.content.*
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.fhv.weatherapp.adapters.HeaderListAdapter
import com.fhv.weatherapp.common.Common
import com.fhv.weatherapp.common.SharedPrefs
import com.fhv.weatherapp.service.notification.network.NetworkBroadcastReceiver
import com.fhv.weatherapp.service.weatherupdater.ForecastUpdater
import kotlinx.android.synthetic.main.list_view.*
import com.fhv.weatherapp.viewmodel.WeatherViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private var mDrawer: DrawerLayout? = null
    private var toolbar: Toolbar? = null
    private var navigationView: NavigationView? = null
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var listView: ListView? = null

    private var adapter: HeaderListAdapter? = null

    private val broadcastReceiver: BroadcastReceiver = NetworkBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // notify on no internet connection
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(broadcastReceiver, filter)

        // This has to be called always in first/main activity to load previously saved state
        SharedPrefs.initializeSharedPreferences(this)

        if (Common.cityList.isEmpty()) {
            askForPermissionsAndUpdateWeather()
        } else {
            ForecastUpdater.startInBackground()
        }

        /* button.setOnClickListener { ForecastUpdater.updateOnce() } */
        /* setting listener for get location button
        btn_get_location.setOnClickListener { getLocationListener() }*/

        //header filled with mock data
        navigationView = findViewById(R.id.nvView) as NavigationView
        val headerLayout = navigationView!!.getHeaderView(0)
        val cityText = headerLayout.findViewById(R.id.name_of_the_city) as TextView
        cityText.setText("Current location")
        val temperatureText = headerLayout.findViewById(R.id.temperature_header) as TextView
        temperatureText.setText("24")
        val iconWeather = headerLayout.findViewById(R.id.icon_header) as WebView
        prepareIcon(iconWeather, "fog", "medium")


        //first card view
        val temperatureMainView = findViewById(R.id.temperature_main_view) as TextView
        val iconMainView = findViewById(R.id.icon_main_view) as WebView
        val summaryMainView = findViewById(R.id.summary_main_view) as TextView
        val summaryMainView2 = findViewById(R.id.summary_main_view2) as TextView
        val iconWindy = findViewById(R.id.windy_icon) as WebView
        val iconRainy = findViewById(R.id.rainy_icon) as WebView
        val windSpeed = findViewById(R.id.wind_speed) as TextView
        val rainProp = findViewById(R.id.rain_prop) as TextView
        val toolbarTitle = findViewById(R.id.toolbar_title) as TextView




        ViewModelProviders.of(this)
                .get(WeatherViewModel::class.java)
                .getWeather()
                .observe(this, android.arch.lifecycle.Observer { weather ->
                    temperatureMainView.setText(Math.round(weather!!.currentWeather.temperature).toString() + " \u2103")
                    prepareIcon(iconMainView, weather!!.currentWeather.icon, "large")
                    summaryMainView.setText(weather!!.currentWeather.summary)
                    summaryMainView2.setText(weather!!.currentWeather.summary)
                    prepareIcon(iconWindy, "wind", "tiny")
                    prepareIcon(iconRainy, "rain", "tiny")
                    windSpeed.setText(weather!!.currentWeather.windSpeed.toString() + " m/s")
                    rainProp.setText((weather!!.currentWeather.precipProbability * 100).toInt().toString() + "%")
                    toolbarTitle.setText("Current location")
                 })



        val valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)
        valueAnimator.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.duration = 9000L

        valueAnimator.addUpdateListener{
            var progress =  it.animatedValue as Float
            var width = summaryMainView.getWidth()
            var translationX = width * progress
            summaryMainView.setTranslationX(-translationX)
            summaryMainView2.setTranslationX(-(translationX - width))

        }
        valueAnimator.start();


        val weatherCard = findViewById(R.id.weather_card) as CardView
        weatherCard.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val intent = Intent(this@MainActivity, WeatherDetails::class.java)
                intent.putExtra("Location", "Current location")
                startActivity(intent)
            }
        })


        // setting listener for get location button
        getWeatherButton.setOnClickListener { askForPermissionsAndUpdateWeather() }

        toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        getSupportActionBar()!!.setDisplayShowTitleEnabled(false)

        mDrawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawerToggle = setupDrawerToggle()
        mDrawer!!.addDrawerListener(drawerToggle)

        listView = findViewById(R.id.list) as ListView
        adapter = HeaderListAdapter(ArrayList(Common.cityList), applicationContext)
        listView!!.setAdapter(adapter)
    }

    // This method is called always before activity ends (usually to save activity state)
    override fun onStop() {

        SharedPrefs.saveCityList()
        SharedPrefs.saveLastCityIndex()

        Log.d(Common.APP_NAME, "onStop")

        super.onStop()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareIcon(icon: WebView, weatherIconType: String, iconSize: String) {
        var iconSizeString = String.format("file:///android_asset/%sWeatherImage.html", iconSize)
        icon.settings.javaScriptEnabled = true
        icon.setLayerType(View.LAYER_TYPE_SOFTWARE, null)  //disabled hardware acceleration.. strangely, it significantly improves performance
        icon.loadUrl(iconSizeString)
        icon.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.loadUrl("javascript:set_icon_type('$weatherIconType')")
            }
        }
    }

    private fun setupDrawerToggle(): ActionBarDrawerToggle {
        return ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open, R.string.drawer_close)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }


    // check if user allowed to use location services
    private fun askForPermissionsAndUpdateWeather() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), Common.PERMISSION_REQUEST_ACCESS_COARSE_LOCATION)
        } else {
            ForecastUpdater.updateOnce()
        }
    }

    // check result of request for permission to use network location services
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            Common.PERMISSION_REQUEST_ACCESS_COARSE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ForecastUpdater.updateOnce()
                } else {
                    Toast.makeText(this, "Permission not granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
