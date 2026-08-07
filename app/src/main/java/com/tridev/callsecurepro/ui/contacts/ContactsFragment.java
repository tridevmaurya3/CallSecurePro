package com.tridev.callsecurepro.ui.contacts;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentContactsBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContactsFragment extends Fragment {

    private FragmentContactsBinding binding;
    private ContactsAdapter contactsAdapter;
    private ExecutorService contactLoader;
    private boolean loadingContacts;

    private final ActivityResultLauncher<String> contactsPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (binding == null) {
                            return;
                        }
                        if (granted) {
                            showContactsContent();
                            loadContacts();
                        } else {
                            showPermissionState();
                        }
                    }
            );

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentContactsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contactLoader = Executors.newSingleThreadExecutor();
        contactsAdapter = new ContactsAdapter(this::openDialerForContact);

        binding.contactsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.contactsRecyclerView.setAdapter(contactsAdapter);
        binding.contactsRecyclerView.setHasFixedSize(false);

        binding.allowContactsButton.setOnClickListener(view1 ->
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        );

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action required.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filtering is performed after the text change is complete.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (contactsAdapter == null) {
                    return;
                }
                String query = editable == null ? "" : editable.toString();
                int visibleCount = contactsAdapter.filter(query);
                updateVisibleContactCount(visibleCount);
                updateEmptyState(visibleCount, !query.trim().isEmpty());
            }
        });

        refreshPermissionAndContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshPermissionAndContacts();
        }
    }

    private void refreshPermissionAndContacts() {
        if (hasContactsPermission()) {
            showContactsContent();
            loadContacts();
        } else {
            showPermissionState();
        }
    }

    private boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionState() {
        binding.permissionCard.setVisibility(View.VISIBLE);
        binding.searchInputLayout.setVisibility(View.GONE);
        binding.listMetaRow.setVisibility(View.GONE);
        binding.contactsRecyclerView.setVisibility(View.GONE);
        binding.loadingIndicator.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.GONE);
        loadingContacts = false;
    }

    private void showContactsContent() {
        binding.permissionCard.setVisibility(View.GONE);
        binding.searchInputLayout.setVisibility(View.VISIBLE);
        binding.listMetaRow.setVisibility(View.VISIBLE);
        binding.contactsRecyclerView.setVisibility(View.VISIBLE);
    }

    private void loadContacts() {
        if (loadingContacts || contactLoader == null || contactLoader.isShutdown()) {
            return;
        }

        loadingContacts = true;
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);

        contactLoader.execute(() -> {
            List<ContactListItem> contacts = queryDeviceContacts();

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null) {
                    return;
                }

                loadingContacts = false;
                binding.loadingIndicator.setVisibility(View.GONE);

                int totalCount = contactsAdapter.setContacts(contacts);
                CharSequence currentQuery = binding.searchInput.getText();
                String query = currentQuery == null ? "" : currentQuery.toString();
                int visibleCount = contactsAdapter.filter(query);

                updateVisibleContactCount(visibleCount);
                updateEmptyState(visibleCount, !query.trim().isEmpty());

                if (totalCount > 0) {
                    binding.contactsRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @NonNull
    private List<ContactListItem> queryDeviceContacts() {
        Map<Long, ContactListItem> uniqueContacts = new LinkedHashMap<>();

        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.Contacts.STARRED
        };

        String sortOrder = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                + " COLLATE LOCALIZED ASC";

        try (Cursor cursor = requireContext().getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                return new ArrayList<>();
            }

            int contactIdIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            );
            int nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            );
            int numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            );
            int starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED);

            while (cursor.moveToNext()) {
                if (contactIdIndex < 0 || numberIndex < 0) {
                    continue;
                }

                long contactId = cursor.getLong(contactIdIndex);
                if (uniqueContacts.containsKey(contactId)) {
                    continue;
                }

                String number = cursor.getString(numberIndex);
                if (number == null || number.trim().isEmpty()) {
                    continue;
                }

                String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                if (name == null || name.trim().isEmpty()) {
                    name = number;
                }

                boolean favorite = starredIndex >= 0 && cursor.getInt(starredIndex) == 1;

                uniqueContacts.put(
                        contactId,
                        new ContactListItem(
                                contactId,
                                name.trim(),
                                number.trim(),
                                favorite
                        )
                );
            }
        } catch (SecurityException ignored) {
            return new ArrayList<>();
        }

        List<ContactListItem> contacts = new ArrayList<>(uniqueContacts.values());
        contacts.sort(Comparator.comparing(
                ContactListItem::getDisplayName,
                String.CASE_INSENSITIVE_ORDER
        ));
        return contacts;
    }

    private void updateVisibleContactCount(int count) {
        binding.contactCount.setText(getString(R.string.contacts_count_format, count));
    }

    private void updateEmptyState(int visibleCount, boolean hasSearchQuery) {
        if (visibleCount > 0) {
            binding.emptyState.setVisibility(View.GONE);
            binding.contactsRecyclerView.setVisibility(View.VISIBLE);
            return;
        }

        binding.contactsRecyclerView.setVisibility(View.GONE);
        binding.emptyState.setVisibility(View.VISIBLE);

        if (hasSearchQuery && contactsAdapter.getTotalContactCount() > 0) {
            binding.emptyTitle.setText(R.string.contacts_search_empty_title);
            binding.emptyBody.setText(R.string.contacts_search_empty_body);
        } else {
            binding.emptyTitle.setText(R.string.contacts_empty_title);
            binding.emptyBody.setText(R.string.contacts_empty_body);
        }
    }

    private void openDialerForContact(@NonNull ContactListItem contact) {
        Intent dialIntent = new Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts("tel", contact.getPhoneNumber(), null)
        );

        try {
            startActivity(dialIntent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    requireContext(),
                    R.string.contacts_dial_error,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        if (contactLoader != null) {
            contactLoader.shutdownNow();
            contactLoader = null;
        }
        loadingContacts = false;
        contactsAdapter = null;
        binding = null;
        super.onDestroyView();
    }
}
