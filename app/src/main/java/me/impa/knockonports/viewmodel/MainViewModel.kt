/*
 * Copyright (c) 2018 Alexander Yaburov
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package me.impa.knockonports.viewmodel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import me.impa.knockonports.R
import me.impa.knockonports.data.*
import me.impa.knockonports.database.KnocksRepository
import me.impa.knockonports.database.entity.LogEntry
import me.impa.knockonports.database.entity.Sequence
import me.impa.knockonports.ext.default
import me.impa.knockonports.ext.startSequence
import me.impa.knockonports.json.SequenceData
import me.impa.knockonports.json.SequenceStep
import me.impa.knockonports.util.Logging
import me.impa.knockonports.util.info
import me.impa.knockonports.util.toast
import me.impa.knockonports.util.warn
import me.impa.knockonports.widget.KnocksWidget

class MainViewModel(application: Application): AndroidViewModel(application), Logging {

    private val repository by lazy { KnocksRepository(application) }
    private lateinit var sequenceList: LiveData<List<Sequence>>
    private lateinit var log: Flow<PagingData<LogEntry>>
    private val selectedSequence = MutableLiveData<Sequence?>()
    private val settingsTabIndex = MutableLiveData<Int>()
    private val fabVisible = MutableLiveData<Boolean>().default(true)
    private val activeFragment = MutableLiveData<ActiveFragmentType>().default(ActiveFragmentType.MAIN)
    private val pendingOrderChanges: MutableLiveData<List<Long>> = MutableLiveData()
    private val installedApps = MutableLiveData<List<AppData>?>().default(null)
    private val dirtySequence = Transformations.map(selectedSequence) {
        runBlocking {
            savePendingData()
        }
        it?.copy()
    }
    private val dirtySteps = Transformations.map(selectedSequence) {
        it?.steps?.onEach { e -> e.icmpSizeOffset = it.icmpType?.getOffset() ?: 0 }?.toMutableList() ?: mutableListOf()
    }
    fun getSequenceList(): LiveData<List<Sequence>> {
        runBlocking { savePendingData() }
        if (!::sequenceList.isInitialized) {
            runBlocking {
                sequenceList = repository.getSequences()
            }
        }
        return sequenceList
    }

    fun getLog(): Flow<PagingData<LogEntry>> {
        if (!::log.isInitialized) {
            runBlocking {
                log = Pager(PagingConfig(pageSize = 50)) { repository.getLogEntries() }
                    .flow.cachedIn(viewModelScope)
            }
        }
        return log
    }

    fun getSelectedSequence(): LiveData<Sequence?> = selectedSequence

    fun setSelectedSequence(sequence: Sequence?) {
        selectedSequence.value = sequence
    }

    fun getDirtySteps(): LiveData<MutableList<SequenceStep>> = dirtySteps

    fun getDirtySequence(): LiveData<Sequence?> = dirtySequence

    fun getSettingsTabIndex(): LiveData<Int> = settingsTabIndex

    fun setSettingsTabIndex(index: Int) {
        settingsTabIndex.value = index
    }

    fun getFabVisible(): LiveData<Boolean> = fabVisible

    fun setFabVisible(isVisible: Boolean) {
        fabVisible.value = isVisible
    }

    fun getActiveFragment(): LiveData<ActiveFragmentType> = activeFragment

    fun setActiveFragment(fragment: ActiveFragmentType) {
        activeFragment.value = fragment
    }

    fun getInstalledApps(): LiveData<List<AppData>?> = installedApps

    fun setInstalledApps(appList: List<AppData>?) {
        installedApps.value = appList
    }

    fun setPendingDataChanges(changes: List<Long>) {
        pendingOrderChanges.value = changes
    }

    fun findSequence(id: Long): Sequence? = runBlocking { repository.findSequence(id) }

    override fun onCleared() {
        super.onCleared()
        runBlocking { savePendingData() }
    }

    fun deleteSequence(sequence: Sequence) {
        sequence.id ?: return
        runBlocking {
            savePendingData()
            repository.deleteSequence(sequence)
            addToLog(EventType.SEQUENCE_DELETED, data = listOf(sequence.name))
            viewModelScope.launch {
                updateWidgets()
                if (sequence.id == selectedSequence.value?.id) {
                    selectedSequence.value = null
                }
            }
        }
    }

    private fun addToLog(event: EventType, date: Long = System.currentTimeMillis(), data: List<String?>? = null) = runBlocking {
        repository.saveLogEntry(LogEntry(null, date, event, data))
    }


    fun clearLog() = runBlocking {
        repository.clearLogEntries()
    }

    fun createEmptySequence() {
        setSelectedSequence(Sequence(null, null, null,
                null, 500, null, null, IcmpType.WITHOUT_HEADERS,
                listOf(SequenceStep(SequenceStepType.UDP, null, null, null, null, ContentEncoding.RAW).apply {
                    icmpSizeOffset = IcmpType.WITHOUT_HEADERS.getOffset()
                }), DescriptionType.DEFAULT, null, ProtocolVersionType.PREFER_IPV4))
    }

    fun saveDirtyData() {
        val seq = dirtySequence.value
        seq ?: return
        if (seq.id == null) {
            seq.order = pendingOrderChanges.value?.size ?: sequenceList.value?.size ?: 0
        }
        seq.steps = dirtySteps.value

        runBlocking {
            repository.saveSequence(seq)
            addToLog(EventType.SEQUENCE_SAVED, data = listOf(seq.name))
            viewModelScope.launch {
                updateWidgets()
            }
        }
    }

    fun knock(sequence: Sequence) {
        val seqId = sequence.id ?: return
        val application = getApplication<Application>()
        application.startSequence(seqId)
    }

    private fun savePendingData() {
        val data = pendingOrderChanges.value
        data ?: return
        val changes = mutableListOf<Sequence>()
        data.forEachIndexed { index, l ->
            val seq = sequenceList.value?.firstOrNull { it.id == l }
            if (seq != null && seq.order != index) {
                seq.order = index
                changes.add(seq)
            }
        }
        if (changes.size > 0) {
            runBlocking {
                repository.updateSequences(changes)
            }
        }
    }

    fun exportSequences(fileName: String, writer: (data: String) -> Unit) {
        val application = getApplication<Application>()
        try {
            info { "Exporting data to $fileName" }

            val data = sequenceList.value?.asSequence()?.map { SequenceData.fromEntity(it) }?.toList()
                    ?: return

            runBlocking {
                withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                    writer.invoke(SequenceData.toJson(data))
                }
            }

            addToLog(EventType.EXPORT, data = listOf(fileName, data.size.toString()))

            viewModelScope.launch {
                application.toast(
                    application.resources.getString(
                        R.string.export_success,
                        fileName
                    )
                )
            }
            info { "Export complete" }

        } catch (e: Exception) {
            warn("Unable to export data", e)
            addToLog(EventType.ERROR_EXPORT, data = listOf(fileName, e.message))
            viewModelScope.launch {
                application.toast(R.string.error_export)
            }
        }
    }

    fun importSequences(fileName: String, reader: () -> String) {
        val application = getApplication<Application>()
        try {
            val data = SequenceData.fromJson(reader.invoke())
            val listSize = sequenceList.value?.size ?: 0
            data.forEachIndexed { index, sequenceData ->
                val seq = sequenceData.toEntity().apply { order = listSize + index }
                runBlocking {
                    withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                        repository.saveSequence(seq)
                    }
                }
            }
            addToLog(EventType.IMPORT, data = listOf(fileName, data.size.toString()))
            viewModelScope.launch {
                application.toast(application.resources.getQuantityString(R.plurals.import_success, data.size, data.size, fileName))
            }
        } catch (e: Exception) {
            warn("Unable to import data", e)
            addToLog(EventType.ERROR_IMPORT, data = listOf(fileName, e.message))
            viewModelScope.launch { application.toast(R.string.error_import) }
        }
    }

    private fun updateWidgets() {
        val application = getApplication<Application>()
        val intent = Intent(application, KnocksWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val widgetManager = AppWidgetManager.getInstance(application)
        val ids = widgetManager.getAppWidgetIds(ComponentName(application, KnocksWidget::class.java))
        widgetManager.notifyAppWidgetViewDataChanged(ids, android.R.id.list)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        application.sendBroadcast(intent)
    }

}