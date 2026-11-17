package fr.leandre.cahier

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Même DA que le cahier : fond sombre, serif italique lavande, lunes. */
class MainActivity : Activity() {

    private val GROUND = Color.parseColor("#14151A")
    private val PANEL = Color.parseColor("#1C1E25")
    private val LINE = Color.parseColor("#2A2C34")
    private val INK = Color.parseColor("#ECEBE6")
    private val DIM = Color.parseColor("#9A9AA3")
    private val ACCENT = Color.parseColor("#B9A7FF")
    private val DANGER = Color.parseColor("#FF8F8F")

    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null
    private var matiereChoisie: String? = null
    private var titreSaisi: String = ""
    private val tick = object : Runnable {
        override fun run() {
            clock?.text = fmt(RecorderService.dureeMs())
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = GROUND
        window.navigationBarColor = GROUND
        window.decorView.setBackgroundColor(GROUND)
    }

    override fun onResume() {
        super.onResume()
        RecorderService.ecouteur = { runOnUiThread { afficher() } }
        afficher()
    }

    override fun onPause() {
        super.onPause()
        RecorderService.ecouteur = null
        handler.removeCallbacks(tick)
    }

    // ---------- écrans ----------

    private fun afficher() {
        handler.removeCallbacks(tick); clock = null
        val attente = Store.enAttente(this)
        when {
            RecorderService.etat != RecorderService.Etat.ARRET -> ecranCapture()
            attente != null -> ecranMatiere(attente.first, attente.second)
            else -> ecranAccueil()
        }
    }

    private fun page(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(36), dp(22), dp(40))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(GROUND); isFillViewport = true; addView(col) }
        setContentView(scroll)
        return col
    }

    private fun ecranAccueil() {
        val p = page()
        p.addView(titre("Le ", "cahier"))
        p.addView(texte("Capte ton cours, même écran éteint. Ensuite, envoie-le au cahier : ton PC le transcrit et Claude en fait le cours.", DIM, 15f).marge(top = 10))

        p.addView(boutonRond().marge(top = 34).centre())
        p.addView(texte("Capter", INK, 15f).apply { gravity = Gravity.CENTER }.marge(top = 10))

        val l = Store.liste(this)
        p.addView(sousTitre("Tes ", "captures").marge(top = 40))
        if (l.isEmpty()) {
            p.addView(carteVide("Rien pour l'instant. Appuie sur Capter au début du cours."))
        } else {
            l.forEach { p.addView(carte(it)) }
        }
        p.addView(bouton("Ouvrir le cahier", false) { ouvrirCahier() }.marge(top = 24))
    }

    private fun ecranCapture() {
        val p = page()
        val pause = RecorderService.etat == RecorderService.Etat.PAUSE
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(View(this@MainActivity).apply {
                background = rond(if (pause) Color.parseColor("#F0C36A") else DANGER)
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(12) }
            })
            addView(titre(if (pause) "En " else "Je ", if (pause) "pause" else "capte"))
        })
        clock = texte(fmt(RecorderService.dureeMs()), INK, 64f).apply {
            typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER
        }
        p.addView(clock!!.marge(top = 48))
        p.addView(texte(
            if (pause) "Appuie sur Reprendre quand le prof reprend."
            else "Tu peux éteindre l'écran et ranger ton téléphone : ça continue. Pose-le près du prof si tu peux.",
            DIM, 15f
        ).apply { gravity = Gravity.CENTER }.marge(top = 16))

        p.addView(bouton(if (pause) "● Reprendre" else "Pause", false) {
            RecorderService.envoyer(this, if (pause) RecorderService.RESUME else RecorderService.PAUSE)
        }.marge(top = 48))
        p.addView(bouton("Terminer", true, DANGER) { RecorderService.envoyer(this, RecorderService.STOP) }.marge(top = 12))
        if (!pause) handler.post(tick)
    }

    private fun ecranMatiere(chemin: String, duree: Long) {
        val p = page()
        if (titreSaisi.isEmpty()) titreSaisi = "Cours du " + SimpleDateFormat("d MMMM", Locale.FRANCE).format(Date())
        p.addView(titre("C'est quelle ", "matière ?"))
        p.addView(texte("Capture de ${fmt(duree)}. Choisis où la ranger.", DIM, 15f).marge(top = 10))

        val chips = FlowLayout(this, dp(8))
        Store.matieres(this).forEach { m ->
            val on = m == matiereChoisie
            val c = Store.couleur(this, m)
            chips.addView(TextView(this).apply {
                text = "●  $m"; textSize = 15f
                setTextColor(if (on) INK else DIM)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = pilule(if (on) melange(c, 0.22f) else PANEL, if (on) c else LINE)
                setOnClickListener { matiereChoisie = m; afficher() }
            })
        }
        p.addView(chips.marge(top = 22))

        val autre = champ("Autre matière : Français, Histoire, SES…")
        p.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(autre, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(bouton("Ajouter", false) {
                val v = autre.text.toString().trim()
                if (v.isNotEmpty()) { matiereChoisie = Store.ajouterMatiere(this@MainActivity, v); afficher() }
            }.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) } })
        }.marge(top = 14))

        p.addView(label("Titre").marge(top = 24))
        val t = champ("Titre du cours").apply { setText(titreSaisi) }
        t.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { titreSaisi = s?.toString() ?: "" }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        p.addView(t.marge(top = 8))

        val ok = matiereChoisie != null
        p.addView(bouton("Ranger et envoyer au cahier", true, if (ok) ACCENT else LINE) {
            if (!ok) { toast("Choisis d'abord la matière."); return@bouton }
            val e = ranger(chemin, duree) ?: return@bouton
            envoyer(e)
        }.marge(top = 28))
        p.addView(bouton("Ranger seulement", false) {
            if (!ok) { toast("Choisis d'abord la matière."); return@bouton }
            if (ranger(chemin, duree) != null) toast("Rangé dans Téléchargements/Cahier.")
            afficher()
        }.marge(top = 10))
        p.addView(texte("Jeter cette capture", DANGER, 14f).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(4))
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setMessage("Jeter cette capture de ${fmt(duree)} pour de bon ?")
                    .setPositiveButton("Jeter") { _, _ -> File(chemin).delete(); Store.setEnAttente(this@MainActivity, null, 0); afficher() }
                    .setNegativeButton("Annuler", null).show()
            }
        })
    }

    private fun ranger(chemin: String, duree: Long): Enregistrement? = try {
        val e = Store.ranger(this, File(chemin), matiereChoisie!!, titreSaisi.trim(), duree)
        Store.setEnAttente(this, null, 0); matiereChoisie = null; titreSaisi = ""
        e
    } catch (ex: Exception) {
        toast("Impossible de ranger l'audio : ${ex.message}"); null
    }

    // ---------- actions ----------

    private fun capter() {
        val manque = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) manque += Manifest.permission.RECORD_AUDIO
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) manque += Manifest.permission.POST_NOTIFICATIONS
        if (manque.isNotEmpty()) { requestPermissions(manque.toTypedArray(), 1); return }
        RecorderService.envoyer(this, RecorderService.START)
        handler.postDelayed({ afficher() }, 300)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) capter()
        else toast("Sans le micro, je ne peux pas capter le cours.")
    }

    private fun envoyer(e: Enregistrement) {
        Store.marquerEnvoye(this, e.uri)
        toast("Dans le cahier, choisis « ${e.nom} » dans Téléchargements/Cahier.")
        ouvrirCahier()
    }

    private fun ouvrirCahier() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Store.CAHIER_URL))) }
        catch (_: Exception) { toast("Aucun navigateur pour ouvrir le cahier.") }
    }

    // ---------- composants ----------

    private fun carte(e: Enregistrement): View {
        val c = Store.couleur(this, e.matiere)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = pilule(PANEL, LINE, dp(14).toFloat())
        }
        box.addView(texte(e.titre.ifEmpty { "Cours" }, INK, 22f).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) })
        val date = SimpleDateFormat("d MMM · HH'h'mm", Locale.FRANCE).format(Date(e.date))
        box.addView(texte("● ${e.matiere}   ·   $date   ·   ${fmt(e.dureeMs)}", c, 13f).marge(top = 4))
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(bouton(if (e.envoye) "Renvoyer au cahier" else "Envoyer au cahier", !e.envoye) { envoyer(e) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(texte("Supprimer", DIM, 13f).apply {
                setPadding(dp(16), dp(10), dp(4), dp(10))
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setMessage("Supprimer « ${e.titre} » de ton téléphone ?")
                        .setPositiveButton("Supprimer") { _, _ -> Store.supprimer(this@MainActivity, e.uri); afficher() }
                        .setNegativeButton("Annuler", null).show()
                }
            })
        }.marge(top = 12))
        return box.marge(top = 12)
    }

    private fun carteVide(s: String) = texte(s, DIM, 14f).apply {
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = pilule(Color.TRANSPARENT, LINE, dp(14).toFloat())
    }.marge(top = 12)

    private fun boutonRond(): View {
        val d = dp(132)
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(d, d)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PANEL); setStroke(dp(2), melange(DANGER, .6f)) }
            addView(View(this@MainActivity).apply { background = rond(DANGER); layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)) })
            setOnClickListener { capter() }
            contentDescription = "Capter le cours"
        }
    }

    private fun titre(a: String, b: String) = TextView(this).apply {
        val s = android.text.SpannableString(a + b)
        s.setSpan(android.text.style.ForegroundColorSpan(ACCENT), a.length, s.length, 0)
        s.setSpan(android.text.style.StyleSpan(Typeface.ITALIC), a.length, s.length, 0)
        text = s; textSize = 46f; setTextColor(INK)
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        setLineSpacing(0f, .9f)
    }

    private fun sousTitre(a: String, b: String) = titre(a, b).apply { textSize = 30f }

    private fun label(s: String) = texte(s.uppercase(), ACCENT, 11f).apply { letterSpacing = .12f; typeface = Typeface.DEFAULT_BOLD }

    private fun texte(s: String, couleur: Int, taille: Float) = TextView(this).apply {
        text = s; setTextColor(couleur); textSize = taille; setLineSpacing(0f, 1.15f)
    }

    private fun champ(indice: String) = EditText(this).apply {
        hint = indice; setHintTextColor(DIM); setTextColor(INK); textSize = 15f
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = pilule(GROUND, LINE, dp(10).toFloat())
    }

    private fun bouton(s: String, plein: Boolean, couleur: Int = ACCENT, clic: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (plein) GROUND else INK)
        background = pilule(if (plein) couleur else Color.TRANSPARENT, if (plein) couleur else LINE, dp(12).toFloat())
        stateListAnimator = null
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { clic() }
    }

    private fun pilule(fond: Int, bord: Int, rayon: Float = dp(99).toFloat()) = GradientDrawable().apply {
        setColor(fond); cornerRadius = rayon; setStroke(dp(1), bord)
    }

    private fun rond(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }

    private fun melange(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun <T : View> T.marge(top: Int = 0): T {
        val lp = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top); layoutParams = lp; return this
    }

    private fun <T : View> T.centre(): T {
        (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL; return this
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format("%02d:%02d", s / 60, s % 60)
    }
}

/** Petite mise en page qui passe à la ligne (pour les pastilles de matières). */
class FlowLayout(ctx: android.content.Context, private val gap: Int) : ViewGroup(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0; var y = 0; var ligne = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            measureChild(c, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > max) { x = 0; y += ligne + gap; ligne = 0 }
            x += c.measuredWidth + gap; ligne = maxOf(ligne, c.measuredHeight)
        }
        setMeasuredDimension(max, y + ligne)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val max = r - l
        var x = 0; var y = 0; var ligne = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x > 0 && x + c.measuredWidth > max) { x = 0; y += ligne + gap; ligne = 0 }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gap; ligne = maxOf(ligne, c.measuredHeight)
        }
    }
}
