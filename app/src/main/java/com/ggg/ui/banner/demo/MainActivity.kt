package com.ggg.ui.banner.demo

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.ggg.ui.banner.CardsBannerAdapter
import com.ggg.ui.banner.CardsBannerView
import com.ggg.ui.banner.CardsBannerViewHolder

class MainActivity : AppCompatActivity() {


    companion object {
        val totalData = intArrayOf(
            R.mipmap.c0,
            R.mipmap.c1,
            R.mipmap.c2,
            R.mipmap.c3,
            R.mipmap.c4,
            R.mipmap.c5,
            R.mipmap.c6,
            R.string.text_1,
            R.string.text_2,
            R.string.text_3,
            R.string.text_4
        )

        val TYPE_IMAGE = 0
        val TYPE_TEXT = 1
    }

    private var resIndex: MutableList<Int> = mutableListOf<Int>()
    private lateinit var adapter: CardsBannerAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        UiThreadBlockWatcher.install(100, UiThreadBlockWatcher.TYPE_LOOPER)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val bannerView: CardsBannerView = findViewById(R.id.banner)
        adapter = object : CardsBannerAdapter() {
            override fun getCount(): Int {
                return resIndex.size
            }

            override fun getItemType(position: Int): Int {
                try {
                    getDrawable(totalData[resIndex[position]])
                    return TYPE_IMAGE
                } catch (e: Exception) {
                    return TYPE_TEXT
                }
            }

            override fun onCreateViewHolder(
                recyclerHolder: CardsBannerViewHolder?,
                itemType: Int
            ): CardsBannerViewHolder {
                if (recyclerHolder != null) {
                    return recyclerHolder
                }
                if (itemType == TYPE_IMAGE) {
                    val cardView = CardView(this@MainActivity)
                    cardView.radius = 30f
                    cardView.cardElevation = 15f
                    cardView.layoutParams = ViewGroup.LayoutParams(600, 600)
                    val imageView = ImageView(this@MainActivity)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    cardView.addView(imageView)
                    val textView = TextView(this@MainActivity)
                    textView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        this.gravity = Gravity.CENTER
                    }
                    textView.setTextColor(Color.WHITE)
                    cardView.addView(textView)
                    val holder = ImageHolder(cardView)
                    imageView.setOnClickListener {
                        Toast.makeText(
                            this@MainActivity,
                            "onclick = " + holder.getAdapterPosition(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return holder
                } else {
                    val cardView = CardView(this@MainActivity)
                    cardView.radius = 30f
                    cardView.cardElevation = 15f
                    cardView.layoutParams = ViewGroup.LayoutParams(600, 600)
                    val textView = TextView(this@MainActivity)
                    textView.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        this.gravity = Gravity.CENTER
                    }
                    cardView.addView(textView)
                    val holder = TextHolder(cardView)
                    textView.setTextColor(Color.RED)
                    textView.gravity = Gravity.CENTER
                    textView.setOnClickListener {
                        Toast.makeText(
                            this@MainActivity,
                            "onclick = " + holder.getAdapterPosition(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return holder
                }
            }

            override fun onBindViewHolder(position: Int, holder: CardsBannerViewHolder) {
                if (holder.getItemViewType() == TYPE_IMAGE) {
                    (holder as ImageHolder).image.setImageResource(totalData[resIndex[position]])
                    (holder as ImageHolder).text.text = "${resIndex[position]}"
                } else {
                    val text = (holder as TextHolder).text
                    text.text = getString(totalData[resIndex[position]])
                }
            }

            override fun onViewRecycled(position: Int, holder: CardsBannerViewHolder) {

            }
        }
        bannerView.setAdapter(adapter)
//        bannerView.startAutoScroll()
        //模拟加载数据耗时
        Handler().postDelayed({
            resIndex = mutableListOf(0, 1, 2, 3, 4, 5, 6)
            adapter.notifyDataSetChanged()
        }, 1000)

        findViewById<Button>(R.id.changeData).setOnClickListener {
            resIndex.shuffle()
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.select).setOnClickListener {
            val last = bannerView.topPosition()
            var select = last
            while (select == last) {
                select = (Math.random() * resIndex.size).toInt()
            }
            val animator = false
            val direct = Math.random()
            val forward = direct > 0.66f
            val backward = direct > 0.33f
            Toast.makeText(
                this@MainActivity,
                "随机选中 = " + select + "，动画 = " + animator + ", 方向 = " + if (forward) "前" else if (backward) "后" else
                    "不限",
                Toast.LENGTH_SHORT
            ).show()
            bannerView.setSelect(select, if (forward) 1 else if (backward) -1 else 0, animator)
        }

        findViewById<Button>(R.id.changeVisible).setOnClickListener {
            val visibleCount = if (Math.random() > 0.5) 5 else 3
            Toast.makeText(
                this@MainActivity,
                "随机可见数 = " + visibleCount + "，old可见数 = " + bannerView.currentVisibleCount(),
                Toast.LENGTH_SHORT
            ).show()
            bannerView.setVisibleCount(visibleCount)
        }
    }
}