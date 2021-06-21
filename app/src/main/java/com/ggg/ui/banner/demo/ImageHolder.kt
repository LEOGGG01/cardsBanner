package com.ggg.ui.banner.demo

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.ggg.ui.banner.CardsBannerViewHolder

class ImageHolder(holderView: View) : CardsBannerViewHolder(holderView) {
    var image = ((holderView as CardView).getChildAt(0) as ImageView)
    var text = ((holderView as CardView).getChildAt(1) as TextView)
}