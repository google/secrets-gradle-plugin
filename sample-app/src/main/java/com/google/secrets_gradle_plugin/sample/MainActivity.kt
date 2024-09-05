// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.secrets_gradle_plugin.sample

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.secrets_plugin.sample.BuildConfig

/**
 * Dummy activity. See build.gradle.kts for sample usage of the plugin.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val buildConfigApiKey = BuildConfig.gmpApiKey
        val manifestApiKey = getMetaValueFromManifest()

        val textView = TextView(this).apply {
            @Suppress("SetTextI18n")
            text = """
                GMP API Key in BuildConfig: $buildConfigApiKey
                GMP API Key in AndroidManifest: $manifestApiKey
            """.trimIndent()
        }
        val linearLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(textView)
        }
        setContentView(linearLayout)
    }

    private fun getMetaValueFromManifest(
        key: String = "com.google.android.geo.API_KEY"
    ): String? {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            appInfo.metaData.getString(key)
        }.getOrNull()
    }
}