package com.clienttools.demo.network

import com.clienttools.demo.DemoApplication
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val authService: AuthService by lazy {
        Retrofit.Builder()
            .baseUrl("http://api.pulse.app/")
            .client(DemoApplication.httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)
    }
}
