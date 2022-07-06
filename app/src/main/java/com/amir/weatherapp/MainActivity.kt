package com.amir.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.amir.weatherapp.models.WeatherResponse
import com.amir.weatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

class MainActivity : AppCompatActivity() {
    //FusedLocationProviderClient: is in order to get the location of latitude and longitude
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //we have to pass a name and a mode to ir
        //the name we define in constant
        //the mode, means this preference must be used just for this application and no other one
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on",
                Toast.LENGTH_LONG
            ).show()
            //redirecting to setting
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            //checking for permissions
            //getting the fine and coarse location
            //in order to it we make use of dexter
            Dexter.withContext(this)  //the context which we want ask for permission
                .withPermissions( //different permissions that we need
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                    /* here we have MultiplePermissionsListener
                    we must call its two functions onPermissionsChecked, onPermissionRationaleShouldBeShown
                     */
                ).withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }

                }).onSameThread().check()  //https://openweathermap.org/price
        }
    }

    private fun isLocationEnabled(): Boolean {
        //this provides access to the system location services
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {

        if (Constants.isNetworkAvailable(this)) {
            // Toast.makeText(this, "You have connected to the internet", Toast.LENGTH_LONG).show()
            //we build retrofit
            //base on the url
            //we pass to it the GSON convertor factory to convert the data in right format
            //than build it. By default take the UTF_8
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> =
                service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)
            //till here we prepare everything
            //than before enqueue we call the progress dialog, because in enqueue(or downloading) we do sth. in background
            showCustomDialogProgress()
            listCall.enqueue(object : Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response!!.isSuccessful) {
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()!!
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "Bad connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Errorrrrrrrr", t!!.message.toString())
                    hideProgressDialog()
                }

            })
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show()

        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage(
                "It looks like you have turned off permissions required for this feature. " +
                        "It can be enabled under Application settings "
            )
            .setPositiveButton("GO TO SETTINGS") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    //adding the uri, which is the package name(package name it means we want open up the settings
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }.show()
    }

    // A function to request the current location. Using the fused location provider client
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        //he used LocationRequest() it is deprecated
        //https://stackoverflow.com/questions/66489605/is-constructor-locationrequest-deprecated-in-google-maps-v2
        val mLocationRequest = com.google.android.gms.location.LocationRequest.create()
        mLocationRequest.priority =
            com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        Looper.myLooper()?.let {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest, mLocationCallback, it
            )
        }
    }

    //A location callback object of fused location provider client where we will get the current location details.
    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("current latitude", "$latitude")
            val longitude = mLastLocation.longitude
            Log.i("current Longitude", "$longitude")
            //  super.onLocationResult(locationResult)
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    fun showCustomDialogProgress() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.setCanceledOnTouchOutside(false)

    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("SetTextI18n")
    private fun setupUI() {
        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)
            for (i in weatherList.weather.indices) {
                Log.i("weather name", weatherList.weather.toString())
                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text =
                    weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text = unixTime(weatherList.sys.sunset)

                tv_humidity.text = weatherList.main.humidity.toString() + "%"
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"
                tv_speed.text = weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country

                when (weatherList.weather[i].icon) {
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.clear)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                    "50d" -> iv_main.setImageResource(R.drawable.mist)

                }
            }

        }

    }

    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    @SuppressLint("SimpleDateFormat")
    private fun unixTime(timex: Long): String {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                recreate()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}