package com.tridev.callsecurepro.ui.lookup;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.data.identity.LookupHistoryEntity;
import com.tridev.callsecurepro.databinding.ItemLookupHistoryBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class LookupHistoryAdapter extends RecyclerView.Adapter<LookupHistoryAdapter.HistoryViewHolder> {

    interface Listener {
        void onHistorySelected(@NonNull LookupHistoryEntity item);
    }

    private final List<LookupHistoryEntity> items = new ArrayList<>();
    private final Listener listener;

    LookupHistoryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    void submitList(@NonNull List<LookupHistoryEntity> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLookupHistoryBinding binding = ItemLookupHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new HistoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final ItemLookupHistoryBinding binding;

        HistoryViewHolder(@NonNull ItemLookupHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull LookupHistoryEntity item) {
            String resolvedName = item.resolvedName == null || item.resolvedName.trim().isEmpty()
                    ? binding.getRoot().getContext().getString(R.string.lookup_unknown_name)
                    : item.resolvedName.trim();

            binding.nameText.setText(resolvedName);
            binding.numberText.setText(item.queryNumber);
            binding.sourceText.setText(item.source + " • " + item.riskLevel);

            String initialSource = item.resolvedName == null || item.resolvedName.trim().isEmpty()
                    ? item.queryNumber
                    : item.resolvedName.trim();
            String initial = initialSource == null || initialSource.trim().isEmpty()
                    ? "#"
                    : initialSource.trim().substring(0, 1).toUpperCase(Locale.getDefault());
            binding.initialText.setText(initial);

            binding.getRoot().setOnClickListener(view -> listener.onHistorySelected(item));
        }
    }
}
