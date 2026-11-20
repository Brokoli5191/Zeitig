package app.brokoli5191.zeitig.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
public interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY sort_order, id")
    public fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes ORDER BY sort_order, id")
    public suspend fun all(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :id")
    public fun observe(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id")
    public suspend fun get(id: Long): RouteEntity?

    @Insert
    public suspend fun insert(route: RouteEntity): Long

    @Update
    public suspend fun update(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    public suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM routes")
    public suspend fun nextSortOrder(): Int

    @Query("UPDATE routes SET sort_order = :order WHERE id = :id")
    public suspend fun setSortOrder(
        id: Long,
        order: Int,
    )

    @Transaction
    public suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}

@Dao
public interface TripDao {
    @Query("SELECT * FROM trips WHERE route_id = :routeId ORDER BY started_at")
    public fun observe(routeId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE route_id = :routeId ORDER BY started_at")
    public suspend fun all(routeId: Long): List<TripEntity>

    @Insert
    public suspend fun insert(trip: TripEntity): Long

    @Query("DELETE FROM trips WHERE route_id = :routeId")
    public suspend fun deleteForRoute(routeId: Long)

    /** Keeps the newest [keep] trips of a route. */
    @Query(
        "DELETE FROM trips WHERE route_id = :routeId AND id NOT IN " +
            "(SELECT id FROM trips WHERE route_id = :routeId ORDER BY started_at DESC LIMIT :keep)",
    )
    public suspend fun prune(
        routeId: Long,
        keep: Int,
    )

    @Transaction
    public suspend fun insertAndPrune(
        trip: TripEntity,
        keep: Int,
    ): Long {
        val id = insert(trip)
        prune(trip.routeId, keep)
        return id
    }
}
