package com.ggg.ui.banner

abstract class CardsBannerAdapter {

    private var cardsBannerView: CardsBannerView? = null

    fun notifyDataSetChanged() {
        cardsBannerView?.onDataSetChanged()
    }

    internal fun bindToBanner(cardsBannerView: CardsBannerView) {
        this.cardsBannerView = cardsBannerView
    }

    internal fun unbindToBanner() {
        this.cardsBannerView = null
    }

    fun boundCardsBanner(): CardsBannerView? {
        return cardsBannerView
    }

    abstract fun getCount(): Int

    abstract fun getItemType(position: Int): Int

    abstract fun onCreateViewHolder(recyclerHolder:CardsBannerViewHolder?, itemType: Int): CardsBannerViewHolder

    abstract fun onBindViewHolder(position: Int, holder: CardsBannerViewHolder)

    abstract fun onViewRecycled(position: Int, holder: CardsBannerViewHolder)
}