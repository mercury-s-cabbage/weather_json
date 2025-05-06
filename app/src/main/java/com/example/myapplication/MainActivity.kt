package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val apiKey = "cb9e4e9c096f8f5edd0c7a85ad00c10b"
    private val city = "Irkutsk"
    private val fileName = "weather_data.json"
    private val fileMaxAgeMillis = 5 * 1000L
    private val updateInterval = 5 * 1000L

    private lateinit var textView: TextView
    private var updateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textView = TextView(this)
        setContentView(textView)

        startPeriodicUpdates()
    }

    private fun startPeriodicUpdates() {
        updateJob?.cancel()

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateWeatherData()
                delay(updateInterval)
            }
        }
    }

    private suspend fun updateWeatherData() {
        val file = File(filesDir, fileName)

        val data: String? = if (!file.exists() || isFileOld(file)) {
            textView.text = "Загрузка..."
            val freshData = downloadWeatherData(city, apiKey)
            if (freshData != null) {
                saveToFile(file, freshData)
                freshData
            } else {
                if (file.exists()) file.readText() else null
            }
        } else {
            file.readText()
        }

        if (data != null) {
            displayWeather(data)
        } else {
            textView.text = "Не удалось получить данные"
        }
    }

    private fun isFileOld(file: File): Boolean {
        val lastModified = file.lastModified()
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - lastModified
        val isOld = diff > fileMaxAgeMillis
        return isOld
    }

    private suspend fun downloadWeatherData(city: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        val urlString =
            "https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$apiKey&units=metric&lang=ru"
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun saveToFile(file: File, data: String) {
        if (file.exists()) {
            Log.d("WeatherApp", "Удаляю старый файл, lastModified=${file.lastModified()}")
            file.delete()
        }
        FileOutputStream(file).use { fos ->
            fos.write(data.toByteArray())
        }

        val now = System.currentTimeMillis()
        val success = file.setLastModified(now)
        Log.d("WeatherApp", "Файл сохранён, new lastModified=${file.lastModified()}, setLastModified success=$success")
    }

    private fun displayWeather(data: String) {
        try {
            val json = JSONObject(data)
            val main = json.getJSONObject("main")
            val temp = main.getDouble("temp")

            val weatherArray = json.getJSONArray("weather")
            val description = if (weatherArray.length() > 0) {
                weatherArray.getJSONObject(0).getString("description")
            } else {
                "Нет описания"
            }

            textView.text = "Температура в Иркутске: $temp°C\nПогода: $description"
            Log.d("WeatherApp", "Температура: $temp, Описание: $description")
        } catch (e: Exception) {
            e.printStackTrace()
            textView.text = "Ошибка при обработке данных"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
    }
}
