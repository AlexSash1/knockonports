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

package me.impa.knockonports.viewadapter

import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import me.impa.knockonports.R
import me.impa.knockonports.database.entity.Sequence
import me.impa.knockonports.databinding.SequenceElementBinding
import me.impa.knockonports.ext.ItemTouchHelperAdapter

class SequenceAdapter(val context: Context): androidx.recyclerview.widget.RecyclerView.Adapter<SequenceAdapter.ViewHolder>(), ItemTouchHelperAdapter {

    override var onStartDrag: ((androidx.recyclerview.widget.RecyclerView.ViewHolder) -> Unit)? = null

    var onCreateShortcut: ((sequence: Sequence) -> Unit)? = null
    var onDelete: ((sequence: Sequence) -> Unit)? = null
    var onKnock: ((sequence: Sequence) -> Unit)? = null
    var onClick: ((sequence: Sequence) -> Unit)? = null
    var onMove: ((fromPos: Int, toPos: Int) -> Unit)? = null

    var items: MutableList<Sequence> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(SequenceElementBinding.inflate(LayoutInflater.from(context), parent, false))

        holder.view.setOnClickListener {
            onClick?.invoke(items[holder.layoutPosition])
        }

        return holder
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sequence = items[position]
        holder.sequenceName.text = if (sequence.name.isNullOrBlank()) {
            context.getString(R.string.untitled_sequence)
        } else {
            sequence.name
        }
        holder.moreIcon.setOnClickListener {
            val popupMenu = PopupMenu(context, holder.moreIcon)
            popupMenu.inflate(R.menu.menu_sequence)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported != true ||
                    sequence.name.isNullOrEmpty()) {
                popupMenu.menu.setGroupVisible(R.id.group_shortcut, false)
            }
            popupMenu.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    R.id.action_menu_knock -> {
                        onKnock?.invoke(sequence)
                        true
                    }
                    R.id.action_menu_delete -> {
                        onDelete?.invoke(sequence)
                        true
                    }
                    R.id.action_menu_edit -> {
                        onClick?.invoke(sequence)
                        true
                    }
                    R.id.action_menu_shortcut -> {
                        onCreateShortcut?.invoke(sequence)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
        holder.knockButton.setOnClickListener {
            onKnock?.invoke(sequence)
        }
        holder.textHost.text = if (sequence.host.isNullOrBlank()) {
            context.getString(R.string.host_not_set)
        } else {
            sequence.host
        }
        holder.textPorts.text = sequence.getReadableDescription()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        val i = items.removeAt(fromPosition)
        items.add(toPosition, i)
        notifyItemMoved(fromPosition, toPosition)
        onMove?.invoke(fromPosition, toPosition)
    }

    override fun onItemDismiss(position: Int) {

    }

    class ViewHolder(binding: SequenceElementBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        val view = binding.root
        val sequenceName = binding.sequenceName
        val moreIcon = binding.moreIcon
        val textHost = binding.textHost
        val textPorts = binding.textPorts
        val knockButton = binding.knockButton
    }

}