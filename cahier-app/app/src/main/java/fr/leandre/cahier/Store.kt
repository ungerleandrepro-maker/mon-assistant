package fr.leandre.cahier

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un enregistrement rangé : le fichier est dans Téléchargements/Cahier. */
data class Enregistrement(
    val uri: String,
    val nom: String,
    val matiere: String,
    val titre: String,
    val date: Long,
    val dureeMs: Long,
    val envoye: Boolean,
) {
    fun toJson() = JSONObject()
        .put("uri", uri).put("nom", nom).put("matiere", matiere).put("titre", titre)
        .put("date", date).put("dureeMs", dureeMs).put("envoye", envoye)

    companion object {
        fun fromJson(o: JSONObject) = Enregistrement(
            o.getString("uri"), o.optString("nom"), o.optString("matiere"), o.optString("titre"),
            o.optLong("date"), o.optLong("dureeMs"), o.optBoolean("envoye"),
        )
    }
}

object Store {
    const val CAHIER_URL = "https://claude.ai/artifact/LmxeKaao9abFDJfXDurLL5#importer"
    private const val PREFS = "cahier"
    val COULEURS = intArrayOf(0xFFB9A7FF.toInt(), 0xFFE8D5C4.toInt(), 0xFFD9805F.toInt(), 0xFF8FB3C6.toInt(), 0xFFD9B44A.toInt(), 0xFFA3B18A.toInt())

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun matieres(c: Context): MutableList<String> {
        val raw = prefs(c).getString("matieres", null) ?: return mutableListOf("Philosophie")
        val a = JSONArray(raw)
        return MutableList(a.length()) { a.getString(it) }
    }

    fun ajouterMatiere(c: Context, nom: String): String {
        val m = matieres(c)
        val existe = m.firstOrNull { it.equals(nom, ignoreCase = true) }
        if (existe != null) return existe
        m.add(nom)
        prefs(c).edit().putString("matieres", JSONArray(m).toString()).apply()
        return nom
    }

    fun couleur(c: Context, matiere: String): Int {
        val i = matieres(c).indexOf(matiere).coerceAtLeast(0)
        return COULEURS[i % COULEURS.size]
    }

    fun liste(c: Context): MutableList<Enregistrement> {
        val raw = prefs(c).getString("enregistrements", "[]")
        val a = JSONArray(raw)
        return MutableList(a.length()) { Enregistrement.fromJson(a.getJSONObject(it)) }
    }

    private fun sauver(c: Context, l: List<Enregistrement>) {
        val a = JSONArray(); l.forEach { a.put(it.toJson()) }
        prefs(c).edit().putString("enregistrements", a.toString()).apply()
    }

    /** Enregistrement terminé mais pas encore rangé (survit si l'appli est fermée). */
    fun enAttente(c: Context): Pair<String, Long>? {
        val p = prefs(c).getString("attente", null) ?: return null
        if (!File(p).exists()) { prefs(c).edit().remove("attente").apply(); return null }
        return p to prefs(c).getLong("attenteDuree", 0)
    }

    fun setEnAttente(c: Context, chemin: String?, duree: Long) {
        val e = prefs(c).edit()
        if (chemin == null) e.remove("attente").remove("attenteDuree") else e.putString("attente", chemin).putLong("attenteDuree", duree)
        e.apply()
    }

    private fun propre(s: String) = s.replace(Regex("[\\\\/:*?\"<>|—]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Copie l'audio dans Téléchargements/Cahier sous le nom « Matière — Titre — date.m4a ». */
    fun ranger(c: Context, source: File, matiere: String, titre: String, dureeMs: Long): Enregistrement {
        val date = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.FRANCE).format(Date(date))
        val nom = "${propre(matiere)} — ${propre(titre).ifEmpty { "Cours" }} — $stamp.m4a"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nom)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cahier")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = c.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Impossible de créer le fichier")
        resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        source.delete()
        val e = Enregistrement(uri.toString(), nom, matiere, titre, date, dureeMs, false)
        val l = liste(c); l.add(0, e); sauver(c, l)
        return e
    }

    fun marquerEnvoye(c: Context, uri: String) {
        sauver(c, liste(c).map { if (it.uri == uri) it.copy(envoye = true) else it })
    }

    fun supprimer(c: Context, uri: String) {
        try { c.contentResolver.delete(Uri.parse(uri), null, null) } catch (_: Exception) {}
        sauver(c, liste(c).filter { it.uri != uri })
    }
}
