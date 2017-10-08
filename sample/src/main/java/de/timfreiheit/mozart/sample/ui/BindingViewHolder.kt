package de.timfreiheit.mozart.sample.ui

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.View

/**
 * ViewHolder which holds an ViewDataBinding
 */
class BindingViewHolder : RecyclerView.ViewHolder {
    private var binding: ViewDataBinding? = null

    constructor(itemView: View) : super(itemView) {
        binding = DataBindingUtil.bind(itemView)
    }

    constructor(binding: ViewDataBinding) : super(binding.root) {
        this.binding = binding
    }

    fun <T : ViewDataBinding> getBinding(): T = binding as T

}