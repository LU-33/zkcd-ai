package com.example.aicreationassistant.ui.navigation

import android.net.Uri

object NavRoutes {
    // Bottom nav destinations
    const val HOME = "home"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"

    // Full screen destinations
    const val TEXT_CREATION = "text_creation/{creationType}"
    const val PRODUCT_DESC = "product_desc"
    const val IMAGE_DESC = "image_desc"
    const val IMAGE_EDIT = "image_edit/{sourceUri}"
    const val DETAIL = "detail/{contentId}"

    // Helpers
    fun textCreation(creationType: String) = "text_creation/$creationType"
    fun imageEdit(sourceUri: String) = "image_edit/${Uri.encode(sourceUri)}"
    fun detail(contentId: Long) = "detail/$contentId"
}
