package com.zcc09.crosshaircompanion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class Profile(
    var id: String,
    var name: String,
    var spec: CrosshairSpec
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("spec", spec.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Style"),
            spec = CrosshairSpec.fromJson(o.optJSONObject("spec") ?: JSONObject())
        )

        fun new(name: String, spec: CrosshairSpec): Profile =
            Profile(UUID.randomUUID().toString(), name, spec)
    }
}

enum class RuleKind { APP, GAMENATIVE_GAME }

class Rule(
    var id: String,
    var label: String,
    var pkg: String?,
    var kind: RuleKind,
    var enabled: Boolean,
    var profileId: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("pkg", pkg ?: JSONObject.NULL)
        put("kind", kind.name)
        put("enabled", enabled)
        put("profileId", profileId ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): Rule {
            val kind = try {
                RuleKind.valueOf(o.optString("kind", "APP"))
            } catch (e: Exception) {
                RuleKind.APP
            }
            return Rule(
                id = o.optString("id", UUID.randomUUID().toString()),
                label = o.optString("label", o.optString("pkg", "Game")),
                pkg = if (o.isNull("pkg")) null else o.optString("pkg").ifBlank { null },
                kind = kind,
                enabled = o.optBoolean("enabled", true),
                profileId = if (o.isNull("profileId")) null else o.optString("profileId").ifBlank { null }
            )
        }

        fun new(label: String, pkg: String?, kind: RuleKind, profileId: String? = null): Rule =
            Rule(UUID.randomUUID().toString(), label, pkg, kind, true, profileId)
    }
}

object Store {
    private const val PREFS = "crosshair_companion"

    const val KEY_PROFILES = "profiles"
    const val KEY_RULES = "rules"
    const val KEY_ACTIVE = "active_profile"
    const val KEY_MASTER = "master_enabled"
    const val KEY_ONLY_SELECTED = "only_selected"
    const val KEY_SEEDED = "seeded"

    const val GAMENATIVE_PKG = "app.gamenative"
    const val GAMENATIVE_GOLD_PKG = "app.gamenative.gold"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun masterEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_MASTER, false)
    fun setMasterEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MASTER, v).apply()

    fun onlySelected(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ONLY_SELECTED, true)
    fun setOnlySelected(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ONLY_SELECTED, v).apply()

    fun loadProfiles(ctx: Context): MutableList<Profile> {
        seedIfNeeded(ctx)
        val raw = prefs(ctx).getString(KEY_PROFILES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Profile>()
            for (i in 0 until arr.length()) list.add(Profile.fromJson(arr.getJSONObject(i)))
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveProfiles(ctx: Context, list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) arr.put(p.toJson())
        prefs(ctx).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun loadRules(ctx: Context): MutableList<Rule> {
        seedIfNeeded(ctx)
        val raw = prefs(ctx).getString(KEY_RULES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Rule>()
            for (i in 0 until arr.length()) list.add(Rule.fromJson(arr.getJSONObject(i)))
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveRules(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        for (r in list) arr.put(r.toJson())
        prefs(ctx).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun activeProfileId(ctx: Context): String {
        seedIfNeeded(ctx)
        return prefs(ctx).getString(KEY_ACTIVE, "") ?: ""
    }

    fun setActiveProfileId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_ACTIVE, id).apply()

    fun activeProfile(ctx: Context): Profile? {
        val list = loadProfiles(ctx)
        val id = activeProfileId(ctx)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    private fun seedIfNeeded(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_SEEDED, false)) return
        val profiles = CrosshairPresets.all().map { Profile.new(it.name, it.spec) }
        saveProfiles(ctx, profiles)
        val active = profiles.firstOrNull { it.name == "Cross + Dot" } ?: profiles.firstOrNull()
        if (active != null) setActiveProfileId(ctx, active.id)
        val rules = mutableListOf<Rule>()
        rules.add(Rule.new("GameNative (all Windows games)", GAMENATIVE_PKG, RuleKind.APP))
        rules.add(Rule.new("GameNative Gold", GAMENATIVE_GOLD_PKG, RuleKind.APP))
        saveRules(ctx, rules)
        p.edit().putBoolean(KEY_SEEDED, true).apply()
    }
}
