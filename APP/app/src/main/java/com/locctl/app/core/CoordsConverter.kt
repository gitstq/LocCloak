package com.locctl.app.core

import kotlin.math.*

object CoordsConverter {
    private const val PI = Math.PI
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0

    fun outOfChina(lat: Double, lng: Double): Boolean {
        return !(lng in 73.66..135.05 && lat in 3.86..53.55)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return (lat + dLat) to (lng + dLng)
    }

    // 迭代反解，误差 < 1e-9 度（与 Node 版完全一致）
    fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) return lat to lng
        var mLat = lat
        var mLng = lng
        for (i in 0 until 12) {
            val (fLat, fLng) = wgs84ToGcj02(mLat, mLng)
            val dLat = fLat - lat
            val dLng = fLng - lng
            mLat -= dLat
            mLng -= dLng
            if (abs(dLat) < 1e-9 && abs(dLng) < 1e-9) break
        }
        return mLat to mLng
    }

    fun bd09ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        val x = lng - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return (z * sin(theta)) to (z * cos(theta))
    }

    // 统一入口：目标输出一律为底层的 WGS-84
    fun toWgs84(lat: Double, lng: Double, sourceSystem: String): Pair<Double, Double> {
        return when (sourceSystem.lowercase()) {
            "gcj02" -> gcj02ToWgs84(lat, lng)
            "bd09" -> {
                val (gLat, gLng) = bd09ToGcj02(lat, lng)
                gcj02ToWgs84(gLat, gLng)
            }
            else -> lat to lng
        }
    }
}

