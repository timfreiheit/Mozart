package de.timfreiheit.mozart.sample.ui.browser;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import de.timfreiheit.mozart.sample.databinding.ItemPlaylistBrowserBinding;
import de.timfreiheit.mozart.sample.ui.BindingViewHolder;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class PlaylistBrowserAdapter extends RecyclerView.Adapter<BindingViewHolder> {

    private final List<String> data = new ArrayList<>();

    private final PublishSubject<String> onItemClicked = PublishSubject.create();

    @Override
    public BindingViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new BindingViewHolder(ItemPlaylistBrowserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(BindingViewHolder holder, int position) {
        ItemPlaylistBrowserBinding binding = holder.getBinding();
        String title = data.get(position);
        binding.title.setText(title);

        binding.getRoot().setOnClickListener(v -> onItemClicked.onNext(title));
    }

    public void setData(List<String> playlists) {
        this.data.clear();
        this.data.addAll(playlists);
        notifyDataSetChanged();
    }

    public Observable<String> onItemClicked() {
        return onItemClicked;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
