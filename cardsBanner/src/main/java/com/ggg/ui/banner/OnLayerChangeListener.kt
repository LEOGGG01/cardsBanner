package com.ggg.ui.banner

interface OnLayerChangeListener {
    fun onLayerTop(position: Int, holder: CardsBannerViewHolder)
    fun onLayerBack(position: Int, holder: CardsBannerViewHolder)
    fun onLayerScrolled(position: Int, positionOffset: Float)
    fun onLayerScrollStateChanged(state: Int)
}