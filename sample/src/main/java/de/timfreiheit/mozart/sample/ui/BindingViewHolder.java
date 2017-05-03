
package de.timfreiheit.mozart.sample.ui;

import android.databinding.DataBindingUtil;
import android.databinding.ViewDataBinding;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * ViewHolder which holds an ViewDataBinding
 */
public class BindingViewHolder extends RecyclerView.ViewHolder {
    private ViewDataBinding binding;

    public BindingViewHolder(View itemView) {
        super(itemView);
        binding = DataBindingUtil.bind(itemView);
    }

    public BindingViewHolder(ViewDataBinding binding){
        super(binding.getRoot());
        this.binding = binding;
    }

    @SuppressWarnings("unchecked")
    public <T extends ViewDataBinding> T getBinding(){
        return (T) binding;
    }

}