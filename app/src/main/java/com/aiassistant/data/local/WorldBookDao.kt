package com.aiassistant.data.local

import androidx.room.*
import com.aiassistant.domain.model.WorldBook
import com.aiassistant.domain.model.WorldBookEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {

    // ============ 世界书主体 ============

    @Query("SELECT * FROM world_books ORDER BY createdAt DESC")
    fun getAllBooks(): Flow<List<WorldBook>>

    @Query("SELECT * FROM world_books ORDER BY createdAt DESC")
    suspend fun getAllBooksList(): List<WorldBook>

    @Query("SELECT * FROM world_books WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getAllEnabledBooksList(): List<WorldBook>

    @Query("SELECT * FROM world_books WHERE id = :id")
    suspend fun getBookById(id: Long): WorldBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: WorldBook): Long

    @Update
    suspend fun updateBook(book: WorldBook)

    @Delete
    suspend fun deleteBook(book: WorldBook)

    @Query("DELETE FROM world_books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE world_books SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setBookEnabled(id: Long, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    // ============ 世界书词条 ============

    @Query("SELECT * FROM world_book_entries WHERE bookId = :bookId ORDER BY priority DESC, createdAt DESC")
    fun getEntriesForBook(bookId: Long): Flow<List<WorldBookEntry>>

    @Query("SELECT * FROM world_book_entries WHERE bookId = :bookId ORDER BY priority DESC, createdAt DESC")
    suspend fun getEntriesForBookList(bookId: Long): List<WorldBookEntry>

    @Query("SELECT * FROM world_book_entries WHERE isEnabled = 1 ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllEnabledEntries(): List<WorldBookEntry>

    @Query("""
        SELECT e.* FROM world_book_entries e
        INNER JOIN world_books b ON e.bookId = b.id
        WHERE b.isEnabled = 1 AND e.isEnabled = 1
        ORDER BY e.priority DESC, e.createdAt DESC
    """)
    suspend fun getActiveEntriesFromEnabledBooks(): List<WorldBookEntry>

    @Query("""
        SELECT e.* FROM world_book_entries e
        INNER JOIN world_books b ON e.bookId = b.id
        WHERE e.bookId IN (:bookIds) AND b.isEnabled = 1 AND e.isEnabled = 1
        ORDER BY e.priority DESC, e.createdAt DESC
    """)
    suspend fun getActiveEntriesForBooks(bookIds: List<Long>): List<WorldBookEntry>

    @Query("SELECT * FROM world_book_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): WorldBookEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorldBookEntry): Long

    @Update
    suspend fun updateEntry(entry: WorldBookEntry)

    @Delete
    suspend fun deleteEntry(entry: WorldBookEntry)

    @Query("DELETE FROM world_book_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM world_book_entries WHERE bookId = :bookId")
    suspend fun deleteEntriesForBook(bookId: Long)

    @Query("UPDATE world_book_entries SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEntryEnabled(id: Long, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM world_book_entries WHERE bookId = :bookId")
    suspend fun getEntryCountForBook(bookId: Long): Int

    @Query("""
        SELECT * FROM world_book_entries 
        WHERE name LIKE '%' || :query || '%' 
           OR `keys` LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%'
        ORDER BY priority DESC, updatedAt DESC
    """)
    fun searchEntries(query: String): Flow<List<WorldBookEntry>>
}
