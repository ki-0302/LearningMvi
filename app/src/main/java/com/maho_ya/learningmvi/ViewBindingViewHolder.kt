package com.maho_ya.learningmvi

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

// out: 共変。戻り値にしか使えない。valはgetterのみなので設定できる。インスタンスではスーパークラスにサブクラスを代入できるようになる
abstract class ViewBindingViewHolder<Item : ViewBindingAdapterItem, out VB : ViewBinding>(
    protected val binding: VB
) : RecyclerView.ViewHolder(binding.root) {
    abstract fun bind(item: Item)

    open fun bind(item: Item, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            bind(item = item)
        }
    }
}
