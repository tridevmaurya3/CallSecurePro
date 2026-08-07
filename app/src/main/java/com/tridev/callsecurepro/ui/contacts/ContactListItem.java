package com.tridev.callsecurepro.ui.contacts;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ContactListItem {

    private final long contactId;
    @NonNull
    private final String displayName;
    @NonNull
    private final List<String> phoneNumbers;
    private final boolean favorite;

    public ContactListItem(
            long contactId,
            @NonNull String displayName,
            @NonNull List<String> phoneNumbers,
            boolean favorite
    ) {
        this.contactId = contactId;
        this.displayName = displayName;
        this.phoneNumbers = Collections.unmodifiableList(new ArrayList<>(phoneNumbers));
        this.favorite = favorite;
    }

    public long getContactId() {
        return contactId;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    @NonNull
    public String getPhoneNumber() {
        return phoneNumbers.isEmpty() ? "" : phoneNumbers.get(0);
    }

    public int getAdditionalNumberCount() {
        return Math.max(0, phoneNumbers.size() - 1);
    }

    public boolean isFavorite() {
        return favorite;
    }

    @NonNull
    public String getInitial() {
        String trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }
}
