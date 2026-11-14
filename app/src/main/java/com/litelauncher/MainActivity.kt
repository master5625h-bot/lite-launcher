package com.litelauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {

    data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    private var allApps = listOf<AppInfo>()
    private var filtered = listOf<AppInfo>()
    private lateinit var listView: ListView
    private lateinit var favRow: LinearLayout
    private lateinit var favHint: TextView
    private val adapter = AppListAdapter()

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * dp).toInt()
            setPadding(p, (40 * dp).toInt(), p, p)
        }

        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSingleLine(true)
            setBackgroundResource(R.drawable.search_bg)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
        }

        val favLabel = TextView(this).apply {
            text = "Favorites"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        favHint = TextView(this).apply {
            text = "Long-press any app below to pin it here"
            setTextColor(0x80FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
        }

        favRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val favScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(favRow)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
        }

        listView = ListView(this).apply {
            divider = null
            dividerHeight = 0
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.TRANSPARENT)
            adapter = this@MainActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(search)
        root.addView(favLabel)
        root.addView(favHint)
        root.addView(favScroll)
        root.addView(listView)
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        listView.setOnItemClickListener { _, _, pos, _ ->
            launchApp(filtered[pos].packageName)
        }
        listView.setOnItemLongClickListener { _, _, pos, _ ->
            showAppOptions(filtered[pos])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        allApps = resolved.map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(pm)
            )
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        applyFilter("")
        refreshFavorites()
    }

    private fun applyFilter(query: String) {
        filtered = if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        adapter.notifyDataSetChanged()
    }

    private fun launchApp(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
        startActivity(i)
    }

    private fun getFavorites(): MutableList<String> {
        val raw = prefs.getString("favs", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveFavorites(favs: List<String>) {
        prefs.edit().putString("favs", favs.joinToString(",")).apply()
    }

    private fun refreshFavorites() {
        favRow.removeAllViews()
        val favs = getFavorites()
        favHint.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        val dp = resources.displayMetrics.density
        for (pkg in favs) {
            val app = allApps.find { it.packageName == pkg } ?: continue
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val p = (8 * dp).toInt()
                setPadding(p, p, p, p)
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val s = (48 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
            }
            val name = TextView(this).apply {
                text = if (app.label.length > 10) app.label.take(9) + "…" else app.label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
            }
            item.addView(icon)
            item.addView(name)
            item.setOnClickListener { launchApp(pkg) }
            item.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(app.label)
                    .setItems(arrayOf("Remove from favorites")) { _, _ ->
                        val f = getFavorites(); f.remove(pkg); saveFavorites(f); refreshFavorites()
                    }
                    .show()
                true
            }
            favRow.addView(item)
        }
    }

    private fun showAppOptions(app: AppInfo) {
        val favs = getFavorites()
        val isFav = favs.contains(app.packageName)
        val favOption = if (isFav) "Remove from favorites" else "Add to favorites"
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf(favOption, "App info")) { _, which ->
                when (which) {
                    0 -> {
                        val f = getFavorites()
                        if (isFav) f.remove(app.packageName) else f.add(app.packageName)
                        saveFavorites(f)
                        refreshFavorites()
                    }
                    1 -> startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.packageName)
                        )
                    )
                }
            }
            .show()
    }

    inner class AppListAdapter : BaseAdapter() {
        override fun getCount() = filtered.size
        override fun getItem(pos: Int) = filtered[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val dp = resources.displayMetrics.density
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                addView(ImageView(this@MainActivity).apply {
                    tag = "icon"
                    val s = (44 * dp).toInt()
                    layoutParams = LinearLayout.LayoutParams(s, s)
                })
                addView(TextView(this@MainActivity).apply {
                    tag = "label"
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setPadding((14 * dp).toInt(), 0, 0, 0)
                })
            }
            val app = filtered[pos]
            (row.findViewWithTag<ImageView>("icon")).setImageDrawable(app.icon)
            (row.findViewWithTag<TextView>("label")).text = app.label
            return row
        }
    }
}
