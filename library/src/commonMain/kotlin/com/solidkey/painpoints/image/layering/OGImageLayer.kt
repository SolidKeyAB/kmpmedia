package com.solidkey.painpoints.image.layering

data class OGImageLayer(
    var layerItems: MutableList<OGImageLayerItem>? = null // ✅ Allows empty layers with only sublayers
) {
    /**
     * ✅ Removes an item by ID and returns `true` if the layer can be deleted (i.e., no items or sublayers left).
     */
    fun removeItemById(id: String): Boolean {
        // ✅ Remove matching items
        layerItems?.removeAll { it.id == id }

        // ✅ Return true if the layer is completely empty (no items and no sublayers)
        return layerItems.isNullOrEmpty()
    }
}
