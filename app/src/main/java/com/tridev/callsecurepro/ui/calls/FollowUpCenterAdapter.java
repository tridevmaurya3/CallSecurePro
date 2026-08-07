package com.tridev.callsecurepro.ui.calls;

import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ItemFollowUpCenterBinding;

import java.util.Date;

public final class FollowUpCenterAdapter extends ListAdapter<FollowUpCenterItem, FollowUpCenterAdapter.ViewHolder> {

    public interface Listener {
        void onOpenDetails(@NonNull FollowUpCenterItem item);

        void onMarkDone(@NonNull FollowUpCenterItem item);

        void onSnooze(@NonNull FollowUpCenterItem item);
    }

    @NonNull
    private final Listener listener;

    public FollowUpCenterAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFollowUpCenterBinding binding = ItemFollowUpCenterBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class ViewHolder extends RecyclerView.ViewHolder {

        @NonNull
        private final ItemFollowUpCenterBinding binding;

        ViewHolder(@NonNull ItemFollowUpCenterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull FollowUpCenterItem item) {
            long now = System.currentTimeMillis();
            FollowUpCenterItem.Bucket bucket = item.bucket(now);

            binding.contactName.setText(item.displayName);
            binding.phoneNumber.setText(item.number);
            binding.followUpTime.setText(binding.getRoot().getContext().getString(
                    R.string.follow_up_center_when_format,
                    formatDateTime(item.followUpAt)
            ));
            binding.priorityText.setText(binding.getRoot().getContext().getString(
                    R.string.follow_up_center_priority,
                    item.priorityScore
            ) + " • " + item.priorityReason);

            boolean hasNote = !item.noteText.trim().isEmpty();
            binding.noteText.setVisibility(hasNote ? View.VISIBLE : View.GONE);
            binding.noteText.setText(item.noteText);

            binding.missingCallText.setVisibility(
                    item.callLogAvailable ? View.GONE : View.VISIBLE
            );
            binding.detailButton.setEnabled(item.callLogAvailable);

            boolean completed = bucket == FollowUpCenterItem.Bucket.DONE;
            binding.doneButton.setVisibility(completed ? View.GONE : View.VISIBLE);
            binding.snoozeButton.setVisibility(completed ? View.GONE : View.VISIBLE);

            bindStatus(bucket);

            binding.detailButton.setOnClickListener(view -> listener.onOpenDetails(item));
            binding.doneButton.setOnClickListener(view -> listener.onMarkDone(item));
            binding.snoozeButton.setOnClickListener(view -> listener.onSnooze(item));
        }

        private void bindStatus(@NonNull FollowUpCenterItem.Bucket bucket) {
            int textRes;
            int foreground;
            int background;
            switch (bucket) {
                case OVERDUE:
                    textRes = R.string.follow_up_center_overdue;
                    foreground = R.color.csp_spam;
                    background = R.color.csp_spam_container;
                    break;
                case UPCOMING:
                    textRes = R.string.follow_up_center_upcoming;
                    foreground = R.color.csp_primary;
                    background = R.color.csp_primary_container;
                    break;
                case DONE:
                default:
                    textRes = R.string.follow_up_center_done;
                    foreground = R.color.csp_safe;
                    background = R.color.csp_safe_container;
                    break;
            }
            binding.statusChip.setText(textRes);
            binding.statusChip.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    foreground
            ));
            binding.statusChip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(binding.getRoot().getContext(), background)
            ));
        }

        @NonNull
        private String formatDateTime(long timestamp) {
            Date date = new Date(timestamp);
            return DateFormat.getMediumDateFormat(binding.getRoot().getContext()).format(date)
                    + " • "
                    + DateFormat.getTimeFormat(binding.getRoot().getContext()).format(date);
        }
    }

    private static final DiffUtil.ItemCallback<FollowUpCenterItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull FollowUpCenterItem oldItem,
                        @NonNull FollowUpCenterItem newItem
                ) {
                    return oldItem.callLogId == newItem.callLogId;
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull FollowUpCenterItem oldItem,
                        @NonNull FollowUpCenterItem newItem
                ) {
                    return oldItem.followUpAt == newItem.followUpAt
                            && oldItem.followUpDone == newItem.followUpDone
                            && oldItem.priorityScore == newItem.priorityScore
                            && oldItem.callLogAvailable == newItem.callLogAvailable
                            && oldItem.displayName.equals(newItem.displayName)
                            && oldItem.number.equals(newItem.number)
                            && oldItem.noteText.equals(newItem.noteText)
                            && oldItem.priorityReason.equals(newItem.priorityReason);
                }
            };
}
