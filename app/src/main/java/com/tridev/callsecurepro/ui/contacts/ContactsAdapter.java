package com.tridev.callsecurepro.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.databinding.ItemContactBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContactsAdapter extends ListAdapter<ContactListItem, ContactsAdapter.ContactViewHolder> {

    public interface OnContactCallClickListener {
        void onCallClick(@NonNull ContactListItem contact);
    }

    private final List<ContactListItem> allContacts = new ArrayList<>();
    @NonNull
    private final OnContactCallClickListener callClickListener;

    public ContactsAdapter(@NonNull OnContactCallClickListener callClickListener) {
        super(DIFF_CALLBACK);
        this.callClickListener = callClickListener;
    }

    public void setContacts(@NonNull List<ContactListItem> contacts) {
        allContacts.clear();
        allContacts.addAll(contacts);
        submitList(new ArrayList<>(contacts));
    }

    public void filter(@NonNull String query) {
        String normalizedQuery = query.trim().toLowerCase(Locale.getDefault());

        if (normalizedQuery.isEmpty()) {
            submitList(new ArrayList<>(allContacts));
            return;
        }

        List<ContactListItem> filtered = new ArrayList<>();
        for (ContactListItem contact : allContacts) {
            String name = contact.getDisplayName().toLowerCase(Locale.getDefault());
            String number = contact.getPhoneNumber().toLowerCase(Locale.getDefault());
            if (name.contains(normalizedQuery) || number.contains(normalizedQuery)) {
                filtered.add(contact);
            }
        }

        submitList(filtered);
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContactBinding binding = ItemContactBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ContactViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {

        private final ItemContactBinding binding;

        ContactViewHolder(@NonNull ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ContactListItem contact) {
            binding.contactInitial.setText(contact.getInitial());
            binding.contactName.setText(contact.getDisplayName());
            binding.contactNumber.setText(contact.getPhoneNumber());
            binding.favoriteIcon.setVisibility(contact.isFavorite() ? View.VISIBLE : View.GONE);
            binding.callAction.setOnClickListener(view -> callClickListener.onCallClick(contact));
        }
    }

    private static final DiffUtil.ItemCallback<ContactListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ContactListItem oldItem,
                        @NonNull ContactListItem newItem
                ) {
                    return oldItem.getContactId() == newItem.getContactId()
                            && oldItem.getPhoneNumber().equals(newItem.getPhoneNumber());
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ContactListItem oldItem,
                        @NonNull ContactListItem newItem
                ) {
                    return oldItem.getDisplayName().equals(newItem.getDisplayName())
                            && oldItem.getPhoneNumber().equals(newItem.getPhoneNumber())
                            && oldItem.isFavorite() == newItem.isFavorite();
                }
            };
}
