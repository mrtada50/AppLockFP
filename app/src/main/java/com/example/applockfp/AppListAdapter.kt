package com.example.applockfp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val apps: MutableList<AppInfo>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.appIcon)
        val label: android.widget.TextView = view.findViewById(R.id.appLabel)
        val lockSwitch: com.google.android.material.switchmaterial.SwitchMaterial =
            view.findViewById(R.id.lockSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label

        // تفريغ المستمع قبل ضبط الحالة لتفادي تفعيله أثناء إعادة تدوير العناصر
        holder.lockSwitch.setOnCheckedChangeListener(null)
        holder.lockSwitch.isChecked = app.locked
        holder.lockSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            app.locked = isChecked
            onToggle(app, isChecked)
        }
    }

    override fun getItemCount(): Int = apps.size
}
