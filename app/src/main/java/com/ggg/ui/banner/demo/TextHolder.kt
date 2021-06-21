package com.ggg.ui.banner.demo

import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.ggg.ui.banner.CardsBannerViewHolder

class TextHolder(holderView: View) : CardsBannerViewHolder(holderView) {

    var text = (holderView as CardView).getChildAt(0) as TextView
}