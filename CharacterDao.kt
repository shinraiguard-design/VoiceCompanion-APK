package com.companionai.voiceapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Insert
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("SELECT * FROM characters ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY createdAt ASC")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("SELECT COUNT(*) FROM characters")
    suspend fun count(): Int
}
