package app.brokoli5191.zeitig.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RouteEntity::class, TripEntity::class], version = 1, exportSchema = true)
public abstract class ZeitigDatabase : RoomDatabase() {
    public abstract fun routes(): RouteDao

    public abstract fun trips(): TripDao

    public companion object {
        public const val NAME: String = "zeitig.db"

        public fun create(context: Context): ZeitigDatabase =
            Room.databaseBuilder(context.applicationContext, ZeitigDatabase::class.java, NAME).build()
    }
}
