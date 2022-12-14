/*
 * Copyright (c) 2021 Alexander Yaburov
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

package me.impa.knockonports.ext

import android.content.Context
import androidx.appcompat.app.AlertDialog
import me.impa.knockonports.R

fun Context.askForConfirmation(sequenceName: String?, action: (confirmed: Boolean) -> Unit) {
    AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_knock)
            .setMessage(this.getString(R.string.message_confirm_knock, sequenceName))
            .setIcon(R.mipmap.ic_launcher_round)
            .setPositiveButton(R.string.action_yes) { dialog, _ ->
                dialog.dismiss()
                action.invoke(true)
            }
            .setNegativeButton(R.string.action_no) { dialog, _ ->
                dialog.dismiss()
                action.invoke(false)
            }
            .setOnDismissListener {
                action.invoke(false)
            }
            .show()
}