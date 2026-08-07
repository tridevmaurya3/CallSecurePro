package com.tridev.callsecurepro.ui.calls;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ItemMissedCallAssistantBinding;

import java.util.ArrayList;
import java.util.List;

public final class MissedCallAssistantAdapter extends RecyclerView.Adapter<MissedCallAssistantAdapter.Holder> {

    public interface Listener {
        void onOpenDetails(@NonNull MissedCallerItem item);
        void onCall(@NonNull MissedCallerItem item);
        void onReply(@NonNull MissedCallerItem item);
        void onRemind(@NonNull MissedCallerItem item);
    }

    private final Listener listener;
    private final List<MissedCallerItem> items = new ArrayList<>();

    public MissedCallAssistantAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<MissedCallerItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMissedCallAssistantBinding binding = ItemMissedCallAssistantBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemMissedCallAssistantBinding binding;

        Holder(@NonNull ItemMissedCallAssistantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull MissedCallerItem item) {
            binding.callerName.setText(item.displayName);
            binding.callerNumber.setText(item.number);
            binding.repeatCount.setText(binding.getRoot().getContext().getString(
                    R.string.missed_assistant_count_7,
                    item.count7Days
            ) + " • " + binding.getRoot().getContext().getString(
                    R.string.missed_assistant_count_30,
                    item.totalCount30Days
            ));

            CharSequence relative = DateUtils.getRelativeTimeSpanString(
                    item.latestTimestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            );
            binding.latestTime.setText(relative);

            if (item.priorityScore >= 70) {
                binding.priorityChip.setText(R.string.missed_assistant_priority_high);
                binding.priorityChip.setChipBackgroundColorResource(R.color.csp_spam_container);
                binding.priorityChip.setTextColor(binding.getRoot().getContext().getColor(R.color.csp_spam));
            } else if (item.isRepeatCaller()) {
                binding.priorityChip.setText(R.string.missed_assistant_priority_repeat);
                binding.priorityChip.setChipBackgroundColorResource(R.color.csp_warning_container);
                binding.priorityChip.setTextColor(binding.getRoot().getContext().getColor(R.color.csp_warning));
            } else {
                binding.priorityChip.setText(R.string.missed_assistant_priority_recent);
                binding.priorityChip.setChipBackgroundColorResource(R.color.csp_primary_container);
                binding.priorityChip.setTextColor(binding.getRoot().getContext().getColor(R.color.csp_primary));
            }

            binding.callButton.setEnabled(item.dialable);
            binding.replyButton.setEnabled(item.dialable);
            binding.callButton.setAlpha(item.dialable ? 1f : 0.45f);
            binding.replyButton.setAlpha(item.dialable ? 1f : 0.45f);

            View.OnClickListener detailsListener = view -> listener.onOpenDetails(item);
            binding.getRoot().setOnClickListener(detailsListener);
            binding.callButton.setOnClickListener(view -> listener.onCall(item));
            binding.replyButton.setOnClickListener(view -> listener.onReply(item));
            binding.remindButton.setOnClickListener(view -> listener.onRemind(item));
        }
    }
}
