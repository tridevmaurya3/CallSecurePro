package com.tridev.callsecurepro.ui.calls;

import android.content.Context;
import android.provider.CallLog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ItemCallHistoryBinding;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallsAdapter extends ListAdapter<CallHistoryItem, CallsAdapter.CallViewHolder> {

    public interface OnCallBackClickListener {
        void onCallBack(@NonNull CallHistoryItem item);
    }

    @NonNull
    private final OnCallBackClickListener callbackListener;

    public CallsAdapter(@NonNull OnCallBackClickListener callbackListener) {
        super(DIFF_CALLBACK);
        this.callbackListener = callbackListener;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCallHistoryBinding binding = ItemCallHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CallViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        holder.bind(getItem(position), position);
    }

    class CallViewHolder extends RecyclerView.ViewHolder {

        private final ItemCallHistoryBinding binding;

        CallViewHolder(@NonNull ItemCallHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CallHistoryItem item, int position) {
            Context context = binding.getRoot().getContext();
            boolean hasName = item.getCachedName() != null
                    && !item.getCachedName().trim().isEmpty();

            binding.callerName.setText(item.getDisplayTitle());
            binding.callerNumber.setVisibility(hasName ? View.VISIBLE : View.GONE);
            if (hasName) {
                binding.callerNumber.setText(item.getNumber());
            }

            binding.callTypeIcon.setImageResource(iconForType(item.getType()));
            binding.callMetadata.setText(buildMetadata(context, item));
            binding.callBackButton.setVisibility(isDialable(item.getNumber()) ? View.VISIBLE : View.INVISIBLE);
            binding.callBackButton.setOnClickListener(view -> callbackListener.onCallBack(item));

            boolean showHeader = position == 0
                    || !isSameDay(
                    item.getTimestamp(),
                    getCurrentList().get(position - 1).getTimestamp()
            );

            binding.dateHeader.setVisibility(showHeader ? View.VISIBLE : View.GONE);
            if (showHeader) {
                binding.dateHeader.setText(formatDayHeader(context, item.getTimestamp()));
            }
        }
    }

    private static boolean isDialable(@NonNull String number) {
        String trimmed = number.trim();
        return !trimmed.isEmpty()
                && !trimmed.equalsIgnoreCase("Unknown caller")
                && !trimmed.equalsIgnoreCase("Private number")
                && !trimmed.equalsIgnoreCase("Restricted number");
    }

    @NonNull
    private static String buildMetadata(@NonNull Context context, @NonNull CallHistoryItem item) {
        String type = typeLabel(context, item.getType());
        String time = DateFormat.getTimeFormat(context).format(new Date(item.getTimestamp()));
        String duration = formatDuration(item.getDurationSeconds());

        if (item.getDurationSeconds() > 0) {
            return type + " • " + time + " • " + duration;
        }
        return type + " • " + time;
    }

    @NonNull
    private static String typeLabel(@NonNull Context context, int type) {
        if (type == CallLog.Calls.INCOMING_TYPE) {
            return context.getString(R.string.calls_incoming);
        }
        if (type == CallLog.Calls.OUTGOING_TYPE) {
            return context.getString(R.string.calls_outgoing);
        }
        if (type == CallLog.Calls.MISSED_TYPE) {
            return context.getString(R.string.calls_missed);
        }
        if (type == CallLog.Calls.BLOCKED_TYPE) {
            return context.getString(R.string.calls_blocked);
        }
        if (type == CallLog.Calls.REJECTED_TYPE) {
            return context.getString(R.string.calls_rejected);
        }
        return context.getString(R.string.calls_other);
    }

    private static int iconForType(int type) {
        if (type == CallLog.Calls.INCOMING_TYPE) {
            return R.drawable.ic_call_incoming;
        }
        if (type == CallLog.Calls.OUTGOING_TYPE) {
            return R.drawable.ic_call_outgoing;
        }
        if (type == CallLog.Calls.MISSED_TYPE) {
            return R.drawable.ic_call_missed;
        }
        if (type == CallLog.Calls.BLOCKED_TYPE || type == CallLog.Calls.REJECTED_TYPE) {
            return R.drawable.ic_call_blocked;
        }
        return R.drawable.ic_call_incoming;
    }

    @NonNull
    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%ds", seconds);
    }

    private static boolean isSameDay(long first, long second) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.setTimeInMillis(first);
        b.setTimeInMillis(second);
        return a.get(Calendar.ERA) == b.get(Calendar.ERA)
                && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    @NonNull
    private static String formatDayHeader(@NonNull Context context, long timestamp) {
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        if (isSameDay(target.getTimeInMillis(), today.getTimeInMillis())) {
            return context.getString(R.string.calls_today);
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(target.getTimeInMillis(), yesterday.getTimeInMillis())) {
            return context.getString(R.string.calls_yesterday);
        }

        return DateFormat.getMediumDateFormat(context).format(new Date(timestamp));
    }

    private static final DiffUtil.ItemCallback<CallHistoryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull CallHistoryItem oldItem,
                        @NonNull CallHistoryItem newItem
                ) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull CallHistoryItem oldItem,
                        @NonNull CallHistoryItem newItem
                ) {
                    return oldItem.getNumber().equals(newItem.getNumber())
                            && safeEquals(oldItem.getCachedName(), newItem.getCachedName())
                            && oldItem.getType() == newItem.getType()
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && oldItem.getDurationSeconds() == newItem.getDurationSeconds();
                }
            };

    private static boolean safeEquals(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return first.equals(second);
    }
}
