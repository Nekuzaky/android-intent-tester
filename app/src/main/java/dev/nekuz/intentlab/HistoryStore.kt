package dev.nekuz.intentlab

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.UUID

class HistoryStore private constructor(context: Context) {
    private val historyFile = AtomicFile(File(context.filesDir, "history.txt"))
    private val favouritesFile = AtomicFile(File(context.filesDir, "favourites.txt"))
    private val listeners = LinkedHashSet<() -> Unit>()

    @Volatile
    var history: List<HistoryEntry> = emptyList()
        private set

    @Volatile
    var favourites: List<HistoryEntry> = emptyList()
        private set

    init {
        Background.storage.execute {
            history = read(historyFile)
            favourites = read(favouritesFile)
            notifyListeners()
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun addRecent(spec: IntentSpec, outcome: String): Boolean {
        val entry = HistoryEntry(UUID.randomUUID().toString(), "", System.currentTimeMillis(), outcome, spec)
        if (HistoryCodec.encodeEntry(entry).length > MAX_ENTRY_CHARS) return false
        change(historyFile, { history }, { history = it }) { current ->
            val rest = if (current.firstOrNull()?.spec == spec) current.drop(1) else current
            (listOf(entry) + rest).take(MAX_HISTORY)
        }
        return true
    }

    fun addFavourite(name: String, spec: IntentSpec): Boolean {
        val entry = HistoryEntry(UUID.randomUUID().toString(), name, System.currentTimeMillis(), "", spec)
        if (HistoryCodec.encodeEntry(entry).length > MAX_ENTRY_CHARS) return false
        change(favouritesFile, { favourites }, { favourites = it }) { current ->
            (listOf(entry) + current).take(MAX_FAVOURITES)
        }
        return true
    }

    fun renameFavourite(id: String, name: String) {
        change(favouritesFile, { favourites }, { favourites = it }) { current ->
            current.map { if (it.id == id) it.copy(name = name) else it }
        }
    }

    fun deleteFavourite(id: String) {
        change(favouritesFile, { favourites }, { favourites = it }) { current -> current.filter { it.id != id } }
    }

    fun deleteRecent(id: String) {
        change(historyFile, { history }, { history = it }) { current -> current.filter { it.id != id } }
    }

    fun clearHistory() {
        change(historyFile, { history }, { history = it }) { emptyList() }
    }

    private fun change(
        file: AtomicFile,
        read: () -> List<HistoryEntry>,
        store: (List<HistoryEntry>) -> Unit,
        update: (List<HistoryEntry>) -> List<HistoryEntry>,
    ) {
        Background.storage.execute {
            val updated = update(read())
            store(updated)
            write(file, updated)
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        Background.onMain { listeners.toList().forEach { it() } }
    }

    private fun read(file: AtomicFile): List<HistoryEntry> = try {
        if (file.baseFile.length() > MAX_FILE_BYTES) {
            emptyList()
        } else {
            HistoryCodec.decodeAll(String(file.readFully(), Charsets.UTF_8))
        }
    } catch (error: Throwable) {
        emptyList()
    }

    private fun write(file: AtomicFile, entries: List<HistoryEntry>) {
        if (entries.isEmpty()) {
            try {
                file.delete()
            } catch (error: Throwable) {
                return
            }
            return
        }
        val stream = try {
            file.startWrite()
        } catch (error: Throwable) {
            return
        }
        try {
            stream.write(HistoryCodec.encodeAll(entries).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
        }
    }

    companion object {
        private const val MAX_HISTORY = 100
        private const val MAX_FAVOURITES = 200
        private const val MAX_ENTRY_CHARS = 32 * 1024
        private const val MAX_FILE_BYTES = 16L * 1024 * 1024

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore = instance ?: synchronized(this) {
            instance ?: HistoryStore(context.applicationContext).also { instance = it }
        }
    }
}
