package com.amir.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {
    const val APP_ID: String ="09dd62b751f9b2e06d9a9a25ad884bae"
    const val BASE_URL: String = "http://api.openweathermap.org/data/"
    //if is farenhite or celcuse this val will take care of
    const val METRIC_UNIT: String="metric"
    const val PREFERENCE_NAME = "WeatherAppPreference"
    const val WEATHER_RESPONSE_DATA ="weather-response_data"
    //checking if we have internet available or not
    fun isNetworkAvailable(context: Context): Boolean {
        //it will give us the connectivity service of our system which we get in context
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        //return depending on the version in the last version of API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//for the new version of android sdk we use this code
            //if is empty return false
            val network = connectivityManager.activeNetwork ?: return false
            //if the connectivityManager.activeNetwork is not empty follow code will be run
            //here we get at first the network than check for network capability
            //if it is empty again a false will be returned
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }

        } else {
            //for the old version of android sdk we use follow code
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }


    }
}