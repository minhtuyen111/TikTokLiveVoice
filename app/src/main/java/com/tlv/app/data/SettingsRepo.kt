package com.tlv.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tlv.app.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

class SettingsRepo(private val context: Context) {
    private object K {
        val rate = floatPreferencesKey("rate")
        val pitch = floatPreferencesKey("pitch")
        val lang = stringPreferencesKey("lang")
        val voice = stringPreferencesKey("voice")
        val readUser = booleanPreferencesKey("readUser")
        val readEmoji = booleanPreferencesKey("readEmoji")
        val block = stringPreferencesKey("block")
        val prio = stringPreferencesKey("prio")
        val dup = booleanPreferencesKey("dup")
        val maxLen = intPreferencesKey("maxLen")
        val perMin = intPreferencesKey("perMin")
        val queueMax = intPreferencesKey("queueMax")
        val bridge = stringPreferencesKey("bridge")
        val channel = stringPreferencesKey("channel")
        val dark = booleanPreferencesKey("dark")
        val accent = intPreferencesKey("accent")
        val reduce = booleanPreferencesKey("reduce")
        val notifCtl = booleanPreferencesKey("notifCtl")
        val mFollow = stringPreferencesKey("mFollow")
        val mShare = stringPreferencesKey("mShare")
        val mGift = stringPreferencesKey("mGift")
        val announce = booleanPreferencesKey("announceEv") // khóa mới: mặc định bật đọc tên người tương tác
        val mIdle = stringPreferencesKey("mIdle")
        val idleGap = intPreferencesKey("idleGap")
        val viOffline = booleanPreferencesKey("viOffline")
        val google = booleanPreferencesKey("ttsGoogle")
        val ttsVol = floatPreferencesKey("ttsVol")
        val ttsAuto = booleanPreferencesKey("ttsAuto")
        val readSym = booleanPreferencesKey("readSym")
        val bgStyle = intPreferencesKey("bgStyle")
        val appLang = stringPreferencesKey("appLang")
        val gender = stringPreferencesKey("ttsGender")
        val cloud = booleanPreferencesKey("ttsCloud")
    }

    private fun split(s: String?) = s.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    val flow: Flow<AppSettings> = context.store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            ttsRate = p[K.rate] ?: d.ttsRate,
            ttsPitch = p[K.pitch] ?: d.ttsPitch,
            ttsLang = p[K.lang] ?: d.ttsLang,
            ttsVoice = p[K.voice] ?: d.ttsVoice,
            readUsername = p[K.readUser] ?: d.readUsername,
            readEmoji = p[K.readEmoji] ?: d.readEmoji,
            blockWords = split(p[K.block]),
            priorityWords = split(p[K.prio]),
            skipDuplicates = p[K.dup] ?: d.skipDuplicates,
            maxLength = p[K.maxLen] ?: d.maxLength,
            maxPerMinute = p[K.perMin] ?: d.maxPerMinute,
            queueMax = p[K.queueMax] ?: d.queueMax,
            bridgeUrl = p[K.bridge] ?: d.bridgeUrl,
            lastChannel = p[K.channel] ?: d.lastChannel,
            darkTheme = p[K.dark] ?: d.darkTheme,
            accent = p[K.accent] ?: d.accent,
            reduceMotion = p[K.reduce] ?: d.reduceMotion,
            notifShowControls = p[K.notifCtl] ?: d.notifShowControls,
            memeFollow = p[K.mFollow] ?: d.memeFollow,
            memeShare = p[K.mShare] ?: d.memeShare,
            memeGift = p[K.mGift] ?: d.memeGift,
            announceEvents = p[K.announce] ?: d.announceEvents,
            ttsVolume = p[K.ttsVol] ?: d.ttsVolume,
            ttsAuto = p[K.ttsAuto] ?: d.ttsAuto,
            readSymbols = p[K.readSym] ?: d.readSymbols,
            bgStyle = p[K.bgStyle] ?: d.bgStyle,
            appLang = p[K.appLang] ?: d.appLang,
            ttsGender = p[K.gender] ?: d.ttsGender,
            ttsCloud = p[K.cloud] ?: d.ttsCloud,
            ttsViOffline = p[K.viOffline] ?: d.ttsViOffline,
            ttsGoogle = p[K.google] ?: d.ttsGoogle,
            memeIdle = p[K.mIdle] ?: d.memeIdle,
            memeIdleGap = p[K.idleGap] ?: d.memeIdleGap,
        )
    }

    /** Ghi lại toàn bộ cài đặt từ bản sao đã chỉnh sửa. */
    suspend fun save(s: AppSettings) {
        context.store.edit { p ->
            p[K.rate] = s.ttsRate; p[K.pitch] = s.ttsPitch
            p[K.lang] = s.ttsLang; p[K.voice] = s.ttsVoice
            p[K.readUser] = s.readUsername; p[K.readEmoji] = s.readEmoji
            p[K.block] = s.blockWords.joinToString("\n")
            p[K.prio] = s.priorityWords.joinToString("\n")
            p[K.dup] = s.skipDuplicates
            p[K.maxLen] = s.maxLength; p[K.perMin] = s.maxPerMinute; p[K.queueMax] = s.queueMax
            p[K.bridge] = s.bridgeUrl; p[K.channel] = s.lastChannel
            p[K.dark] = s.darkTheme; p[K.accent] = s.accent; p[K.reduce] = s.reduceMotion
            p[K.notifCtl] = s.notifShowControls
            p[K.mFollow] = s.memeFollow; p[K.mShare] = s.memeShare; p[K.mGift] = s.memeGift
            p[K.announce] = s.announceEvents
            p[K.ttsVol] = s.ttsVolume; p[K.ttsAuto] = s.ttsAuto; p[K.readSym] = s.readSymbols; p[K.bgStyle] = s.bgStyle; p[K.appLang] = s.appLang
            p[K.gender] = s.ttsGender; p[K.cloud] = s.ttsCloud
            p[K.viOffline] = s.ttsViOffline; p[K.google] = s.ttsGoogle; p[K.mIdle] = s.memeIdle; p[K.idleGap] = s.memeIdleGap
        }
    }
}
