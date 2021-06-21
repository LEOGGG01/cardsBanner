package com.ggg.ui.banner

import android.view.View

open class CardsBannerViewHolder(val holdView: View) {

    internal var ownerCardsBannerView: CardsBannerView? = null

    internal var itemType: Int = -1

    fun getAdapterPosition(): Int {
        return ownerCardsBannerView?.getAdapterPositionForHolder(this) ?: -1
    }

    fun getItemViewType(): Int {
        return itemType
    }
}