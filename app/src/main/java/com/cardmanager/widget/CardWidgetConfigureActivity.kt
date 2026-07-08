package com.cardmanager.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import com.cardmanager.R
import com.cardmanager.data.AppDatabase
import com.cardmanager.data.AssetPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardWidgetConfigureActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var plans: List<AssetPlan> = emptyList()
    private lateinit var modeGroup: RadioGroup
    private lateinit var assetSpinner: Spinner
    private val modeIds = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(buildContentView())
        scope.launch {
            plans = withContext(Dispatchers.IO) {
                AppDatabase.get(applicationContext).assetPlanDao().getAllPlans().first()
            }
            bindPlanSpinner()
            restoreSelection()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val padding = dp(22)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(0xFFF8FAFC.toInt())

            addView(TextView(context).apply {
                text = getString(R.string.widget_config_title)
                setTextColor(0xFF0F172A.toInt())
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })

            modeGroup = RadioGroup(context).apply {
                orientation = RadioGroup.VERTICAL
                setPadding(0, dp(18), 0, dp(8))
                addView(modeButton(CardWidgetUpdater.MODE_TASKS, getString(R.string.widget_mode_tasks)))
                addView(modeButton(CardWidgetUpdater.MODE_VAULT_TOTAL, getString(R.string.widget_mode_vault_total)))
                addView(modeButton(CardWidgetUpdater.MODE_ASSET_PLAN, getString(R.string.widget_mode_asset_plan)))
            }
            addView(modeGroup)

            addView(TextView(context).apply {
                text = getString(R.string.widget_select_asset_plan)
                setTextColor(0xFF475569.toInt())
                textSize = 13f
                setPadding(0, dp(12), 0, dp(6))
            })

            assetSpinner = Spinner(context).apply {
                visibility = View.VISIBLE
            }
            addView(assetSpinner)

            addView(Button(context).apply {
                text = getString(R.string.save)
                setAllCaps(false)
                setOnClickListener { saveAndFinish() }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = dp(24)
                layoutParams = params
            })

            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun modeButton(mode: String, label: String): RadioButton =
        RadioButton(this).apply {
            id = View.generateViewId()
            modeIds[mode] = id
            tag = mode
            text = label
            textSize = 15f
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, dp(5), 0, dp(5))
        }

    private fun bindPlanSpinner() {
        val labels = if (plans.isEmpty()) {
            listOf(getString(R.string.widget_no_asset_plan))
        } else {
            plans.map { it.name.ifBlank { getString(R.string.widget_asset_project_title) } }
        }
        assetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun restoreSelection() {
        val config = CardWidgetUpdater.loadConfig(this, appWidgetId)
        val mode = config.mode.takeIf {
            it in setOf(CardWidgetUpdater.MODE_TASKS, CardWidgetUpdater.MODE_VAULT_TOTAL, CardWidgetUpdater.MODE_ASSET_PLAN)
        } ?: CardWidgetUpdater.MODE_TASKS
        modeGroup.check(modeIds[mode] ?: modeIds[CardWidgetUpdater.MODE_TASKS] ?: View.NO_ID)
        val planIndex = plans.indexOfFirst { it.id == config.planId }.takeIf { it >= 0 } ?: 0
        if (plans.isNotEmpty()) assetSpinner.setSelection(planIndex)
    }

    private fun saveAndFinish() {
        val selectedMode = modeGroup.findViewById<RadioButton>(modeGroup.checkedRadioButtonId)
            ?.tag as? String ?: CardWidgetUpdater.MODE_TASKS
        val safeMode = if (selectedMode == CardWidgetUpdater.MODE_ASSET_PLAN && plans.isEmpty()) {
            CardWidgetUpdater.MODE_VAULT_TOTAL
        } else {
            selectedMode
        }
        val planId = plans.getOrNull(assetSpinner.selectedItemPosition)?.id.orEmpty()
        val config = CardWidgetUpdater.Config(safeMode, planId)
        CardWidgetUpdater.saveConfig(this, appWidgetId, config)

        scope.launch {
            val manager = AppWidgetManager.getInstance(applicationContext)
            withContext(Dispatchers.IO) {
                CardWidgetUpdater.updateWidget(applicationContext, manager, appWidgetId)
            }
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, result)
            finish()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
