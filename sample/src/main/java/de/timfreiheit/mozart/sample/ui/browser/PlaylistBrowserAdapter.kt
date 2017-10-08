package de.timfreiheit.mozart.sample.ui.browser

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

import java.util.ArrayList

import de.timfreiheit.mozart.sample.databinding.ItemPlaylistBrowserBinding
import de.timfreiheit.mozart.sample.ui.BindingViewHolder
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class PlaylistBrowserAdapter : RecyclerView.Adapter<BindingViewHolder>() {

    private val data = ArrayList<String>()

    private val onItemClicked = PublishSubject.create<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        return BindingViewHolder(ItemPlaylistBrowserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        val binding = holder.getBinding<ItemPlaylistBrowserBinding>()
        val title = data[position]
        binding.title.text = title

        binding.root.setOnClickListener { onItemClicked.onNext(title) }
    }

    fun setData(playlists: List<String>) {
        this.data.clear()
        this.data.addAll(playlists)
        notifyDataSetChanged()
    }

    fun onItemClicked(): Observable<String> = onItemClicked

    override fun getItemCount(): Int = data.size
}
