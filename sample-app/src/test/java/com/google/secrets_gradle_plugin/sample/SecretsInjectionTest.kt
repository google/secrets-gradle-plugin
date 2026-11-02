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

import com.google.secrets_plugin.sample.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecretsInjectionTest {

    @Test
    fun `secrets properties are injected into BuildConfig and ignored keys are excluded`() {
        assertEquals("API_KEY_VALUE", BuildConfig.gmpApiKey)
        assertEquals("DEFAULT_FALLBACK_VALUE", BuildConfig.defaultFallbackKey)
        assertThrows(NoSuchFieldException::class.java) {
            BuildConfig::class.java.getDeclaredField("keyToIgnore")
        }
        assertThrows(NoSuchFieldException::class.java) {
            BuildConfig::class.java.getDeclaredField("ignoreKey1")
        }
        assertThrows(NoSuchFieldException::class.java) {
            BuildConfig::class.java.getDeclaredField("ignoreKey2")
        }
    }
}
