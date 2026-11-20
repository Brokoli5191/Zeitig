package app.brokoli5191.zeitig.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
public data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "origin_name") val originName: String,
    @ColumnInfo(name = "origin_lat") val originLat: Double?,
    @ColumnInfo(name = "origin_lon") val originLon: Double?,
    @ColumnInfo(name = "destination_name") val destinationName: String,
    @ColumnInfo(name = "destination_lat") val destinationLat: Double?,
    @ColumnInfo(name = "destination_lon") val destinationLon: Double?,
    @ColumnInfo(name = "journey_json") val journeyJson: String,
    /** Bit 0 = Monday … bit 6 = Sunday; 0 = no schedule. */
    @ColumnInfo(name = "schedule_days") val scheduleDays: Int,
    /** "window", "arrive_by" or null. */
    @ColumnInfo(name = "schedule_mode") val scheduleMode: String?,
    @ColumnInfo(name = "window_start_minute") val windowStartMinute: Int?,
    @ColumnInfo(name = "window_end_minute") val windowEndMinute: Int?,
    @ColumnInfo(name = "arrive_by_minute") val arriveByMinute: Int?,
    @ColumnInfo(name = "lead_minutes") val leadMinutes: Int,
    /** Comma-separated minutes before leaving, e.g. "5,3,1,0". */
    val reminders: String,
    @ColumnInfo(name = "buffer_seconds") val bufferSeconds: Int,
    val accessible: Boolean,
    @ColumnInfo(name = "manual_walk_seconds") val manualWalkSeconds: Int?,
    @ColumnInfo(name = "notifications_enabled") val notificationsEnabled: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("route_id")],
)
public data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "route_id") val routeId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "walk_seconds") val walkSeconds: Int,
    val accepted: Boolean,
    @ColumnInfo(name = "planned_leave_at") val plannedLeaveAt: Long?,
    /** "geofence" or "manual" – how arrival was detected. Only the duration is kept, never a track. */
    val arrival: String,
)
