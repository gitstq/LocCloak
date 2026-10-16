package com.locctl.app.core

import android.os.Parcel
import android.os.Parcelable

/**
 * 一个坐标点。lat/lng 统一为 WGS-84，因为设备底层只认 WGS-84。
 * [gcj02] 仅在需要展示「高德看到的值」时填充。
 *
 * 实现 Parcelable 是因为要通过 Intent 传给前台服务。
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val gcj02: Pair<Double, Double>? = null,
) : Parcelable {
    fun formatWgs84(): String = "%.6f, %.6f".format(lat, lng)

    fun formatGcj02(): String = gcj02?.let { "%.6f, %.6f".format(it.first, it.second) } ?: "-"

    private constructor(p: Parcel) : this(
        p.readDouble(),
        p.readDouble(),
        if (p.readInt() == 1) {
            p.readDouble() to p.readDouble()
        } else {
            null
        },
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeDouble(lat)
        dest.writeDouble(lng)
        if (gcj02 == null) {
            dest.writeInt(0)
        } else {
            dest.writeInt(1)
            dest.writeDouble(gcj02.first)
            dest.writeDouble(gcj02.second)
        }
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GeoPoint> {
        override fun createFromParcel(source: Parcel): GeoPoint = GeoPoint(source)
        override fun newArray(size: Int): Array<GeoPoint?> = arrayOfNulls(size)
    }
}

/** 坐标系标识，与 Node 侧 coords.js 的约定一致 */
enum class CoordSystem(val id: String, val label: String) {
    WGS84("wgs84", "WGS-84"),
    GCJ02("gcj02", "GCJ-02 高德"),
    BD09("bd09", "BD-09 百度");

    companion object {
        fun fromId(id: String?): CoordSystem =
            entries.firstOrNull { it.id == id } ?: WGS84
    }
}

/** provider 是否已被 mock 接管，以及最近一次注入的坐标 */
data class MockStatus(
    val mocked: Map<String, Boolean>,
    val lastLocation: Map<String, GeoPoint>,
    val locationEnabled: Boolean,
) {
    val anyMocked: Boolean get() = mocked.values.any { it }

    companion object {
        val EMPTY = MockStatus(emptyMap(), emptyMap(), false)
    }
}

/** 一次注入的结果。channelId 标明走了哪条通道，便于排错。 */
data class InjectResult(
    val channelId: String,
    val providers: List<String>,
    val point: GeoPoint,
    val accuracyMeters: Float,
    val elapsedMs: Long,
)

/** 自检项。每个失败项都要带 [action] 提示用户下一步做什么。 */
data class DoctorCheck(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String,
    val action: String? = null,
)

data class DoctorReport(val checks: List<DoctorCheck>) {
    val allPassed: Boolean get() = checks.all { it.passed }
}
