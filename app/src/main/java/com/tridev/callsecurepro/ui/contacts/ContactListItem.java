package com.tridev.callsecurepro.ui.contacts;

import androidx.annotation.NonNull;

import java.util.Locale;

public class ContactListItem {

    private final long contactId;
    @NonNull
    private final String displayName;
    @NonNull
    private final String phoneNumber;
    private final boolean favorite;

    public ContactListItem(
            long contactId,
            @NonNull String displayName,
            @NonNull String phoneNumber,
            boolean favorite
    ) {
        this.contactId = contactId;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
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
    public String getPhoneNumber() {
        return phoneNumber;
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
