package com.example.locat

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import com.example.locat.ui.theme.LocatTheme
import com.google.android.gms.location.*    //位置情報
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationText by mutableStateOf("")
    private var searchResult by mutableStateOf("")
    private var isLoading by mutableStateOf(false)  //ローディング状態を管理

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("Location", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    locationText =
                        "緯度: ${location.latitude}, 経度: ${location.longitude}"
                }
            }
        }

        // 画面のエッジまでレイアウトを広げる
        WindowCompat.setDecorFitsSystemWindows(window, false)
        //位置情報の権限をリクエスト
        requestLocationPermission()

        setContent {
            LocatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LocationScreen(
                        locationText = locationText,
                        searchResult = searchResult,
                        isLoading = isLoading,
                        onSearchClick = { range ->
                            fetchLocationAndSearchNearbyRestaurants(range)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    //位置情報の権限リクエスト
    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // 権限を説明するためのUIを表示

            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        } else {
            // 権限が拒否された場合の処理
            locationText = "Permission denied. Location access is required for this feature."
        }
    }

    //位置情報の取得
    private fun startLocationUpdates() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = Priority.PRIORITY_HIGH_ACCURACY
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } else {
            locationText = "Location permission not granted."
        }
    }

    //近くの飲食店を検索
    private fun fetchLocationAndSearchNearbyRestaurants(range: Int) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            isLoading = true
            searchResult = "" // 検索前にリセット

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    fetchNearbyRestaurants(it, range)
                } ?: run {
                    isLoading = false
                    searchResult = "Failed to get current location."
                }
            }
        } else {
            searchResult = "Location permission not granted."
        }
    }

    //ホットペッパーAPIの通信(Retrofitを使用)
    private fun fetchNearbyRestaurants(location: Location, range: Int) {
        Log.d(
            "API_CALL",
            "Fetching nearby restaurants for location: ${location.latitude}, ${location.longitude}"
        )

        val retrofit = Retrofit.Builder()
            .baseUrl("https://webservice.recruit.co.jp/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(HpGourmetApiService::class.java)
        val apiKey = "apikey" // 自分のAPIキー

        apiService.getRestaurants(location.latitude, location.longitude, apiKey, range)
            .enqueue(object : retrofit2.Callback<HotPepperResponse> {
                override fun onResponse(
                    call: retrofit2.Call<HotPepperResponse>,
                    response: retrofit2.Response<HotPepperResponse>
                ) {
                    isLoading = false
                    if (response.isSuccessful) {
                        val shopList = response.body()?.results?.shop.orEmpty() // null回避
                        searchResult = if (shopList.isNotEmpty()) {
                            shopList.joinToString("\n") { shop ->
                                "${shop.name}, ${shop.logo_image}, ${shop.access}"
                            }
                        } else {
                            "周辺にお店が見つかりませんでした。"
                        }
                    } else {
                        searchResult = "データの取得に失敗しました。Error code: ${response.code()}"
                    }
                }

                override fun onFailure(call: retrofit2.Call<HotPepperResponse>, t: Throwable) {
                    isLoading = false
                    searchResult = "データの取得に失敗しました：${t.message}"
                }
            })
    }

}

interface HpGourmetApiService {
    @GET("hotpepper/gourmet/v1/")
    fun getRestaurants(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("key") apiKey: String,
        @Query("range") range: Int,
        @Query("count") count: Int = 10,
        @Query("format") format: String = "json"
    ): retrofit2.Call<HotPepperResponse>
}

data class HotPepperResponse(
    val results: Results
)

data class Results(
    val shop: List<Shop>
)

data class Shop(
    val name: String,
    val logo_image: String,
    val access: String
)

@Composable
fun LocationScreen(
    locationText: String,
    searchResult: String,
    isLoading: Boolean,
    onSearchClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchRanges = listOf(1, 2, 3, 4, 5)
    val searchRangeLabels = listOf("300m", "500m", "1km", "2km", "3km")
    var selectedRange by remember { mutableStateOf(searchRanges[0]) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "現在地:")
        Text(text = locationText)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "検索範囲(半径)を選択:")

        Box {
            Button(onClick = { expanded = true }) {
                Text(text = searchRangeLabels[searchRanges.indexOf(selectedRange)])
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                searchRanges.forEachIndexed { index, range ->
                    DropdownMenuItem(
                        text = { Text(text = searchRangeLabels[index]) },
                        onClick = {
                            selectedRange = range
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            onSearchClick(selectedRange)
        }) {
            Text("周辺の飲食店を検索")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Text(text = "検索中...")
        } else {
            Column {
                searchResult.split("\n").forEach { shopInfo ->
                    val info = shopInfo.split(", ")
                    if (info.size == 3) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(info[1]),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(end = 8.dp)
                            )
                            Column {
                                Text(text = info[0], style = MaterialTheme.typography.bodyLarge)
                                Text(text = info[2], style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLocationScreen() {
    LocatTheme {
        LocationScreen(
            // サンプルの情報
            locationText = "緯度: 35, 経度: 139",
            searchResult = """
                Restaurant 1, https://sample.image/1.png, Access info 1
                Restaurant 2, https://sample.image/2.png, Access info 2
            """.trimIndent(),
            isLoading = false,
            onSearchClick = {}
        )
    }
}
