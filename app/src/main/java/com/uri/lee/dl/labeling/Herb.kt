/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uri.lee.dl.labeling

import android.net.Uri

/** Information about a product.  */
data class Herb(
    val imageUrl: String? = null,
    val imageFileUri: Uri? = null,
    val id: String? = null,
    val sciName: String? = null,
    val enName: String? = null,
    val viName: String? = null,
    val confidence: Float? = null,
)

sealed interface HerbEvent {
    data class LabelingError(val exception: Exception) : HerbEvent
    data class ObjectDetectionError(val exception: Exception) : HerbEvent
    data class BitmapError(val exception: Exception) : HerbEvent
    object NoHerbObjects : HerbEvent
}
