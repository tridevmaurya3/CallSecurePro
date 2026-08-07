package com.tridev.callsecurepro.ui.contacts;

import android.telephony.PhoneNumberUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.ItemContactBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContactsAdapter extends ListAdapter<ContactListItem, ContactsAdapter.ContactViewHolder> {

    public interface Listener {
        void onOpenProfile(@NonNull ContactListItem contact);

        void onCall(@NonNull ContactListItem contact);

        void onMessage(@NonNull ContactListItem contact);
    }

    private final List<ContactListItem> allContacts = new ArrayList<>();
    @NonNull
    private final Listener listener;

    public ContactsAdapter(@NonNull Listener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public int setContacts(@NonNull List<ContactListItem> contacts) {
        allContacts.clear();
        allContacts.addAll(contacts);
        submitList(new ArrayList<>(contacts));
        return contacts.size();
    }

    public int filter(@NonNull String query) {
        String normalizedQuery = query.trim().toLowerCase(Locale.getDefault());
        String normalizedNumberQuery = PhoneNumberUtils.normalizeNumber(query);

        if (normalizedQuery.isEmpty()) {
            submitList(new ArrayList<>(allContacts));
            return allContacts.size();
        }

        List<ContactListItem> filtered = new ArrayList<>();
        for (ContactListItem contact : allContacts) {
            String name = contact.getDisplayName().toLowerCase(Locale.getDefault());
            boolean matches = name.contains(normalizedQuery);

            if (!matches) {
                for (String number : contact.getPhoneNumbers()) {
                    String visibleNumber = number.toLowerCase(Locale.getDefault());
                    String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
                    if (visibleNumber.contains(normalizedQuery)
                            || (!normalizedNumberQuery.isEmpty()
                            && normalizedNumber.contains(normalizedNumberQuery))) {
                        matches = true;
                        break;
                    }
                }
            }

            if (matches) {
                filtered.add(contact);
            }
        }

        submitList(filtered);
        return filtered.size();
    }

    public int getTotalContactCount() {
        return allContacts.size();
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

            int additionalNumbers = contact.getAdditionalNumberCount();
            if (additionalNumbers > 0) {
                binding.moreNumbers.setVisibility(View.VISIBLE);
                binding.moreNumbers.setText(
                        binding.getRoot().getContext().getString(
                                R.string.contacts_profile_more_numbers,
                                additionalNumbers
                        )
                );
            } else {
                binding.moreNumbers.setVisibility(View.GONE);
            }

            binding.getRoot().setOnClickListener(view -> listener.onOpenProfile(contact));
            binding.callAction.setOnClickListener(view -> listener.onCall(contact));
            binding.messageAction.setOnClickListener(view -> listener.onMessage(contact));
        }
    }

    private static final DiffUtil.ItemCallback<ContactListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ContactListItem oldItem,
                        @NonNull ContactListItem newItem
                ) {
                    return oldItem.getContactId() == newItem.getContactId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ContactListItem oldItem,
                        @NonNull ContactListItem newItem
                ) {
                    return oldItem.getDisplayName().equals(newItem.getDisplayName())
                            && oldItem.getPhoneNumbers().equals(newItem.getPhoneNumbers())
                            && oldItem.isFavorite() == newItem.isFavorite();
                }
            };
}
