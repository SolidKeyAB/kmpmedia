package com.solidkey.painpoints.image.layering

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.solidkey.painpoints.image.animating.OGAnimationSpec
import com.solidkey.painpoints.image.transform.OGImageTransformConfig
import com.solidkey.painpoints.source.OGSource
import com.solidkey.painpoints.source.OGSourceType

data class OGImageLayerItem(
    val id: String, // ✅ Unique identifier
    val source: OGSourceType,
    var x: Float,
    var y: Float,
    val transformConfig: OGImageTransformConfig? = null,
    val onClick: (() -> Unit)? = null,
    val subLayers: List<OGImageLayer> = emptyList(),
    val modifier: Modifier = Modifier,
    val animationSpec: OGAnimationSpec? = null,
    val triggerAnimation: MutableState<Boolean> = mutableStateOf(false), // ✅ Internal trigger
    val externalTrigger: MutableState<Boolean> = mutableStateOf(false), // ✅ External trigger
    val groupId: String? = null, // ✅ Grouping items together
    val audio: OGSource? = null, // 🎵 New: Audio Source
)
